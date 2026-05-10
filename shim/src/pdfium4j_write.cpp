#include "pdfium4j_shim.h"
#include "pdfium4j_utils.h"
#include <qpdf/Pipeline.hh>
#include <qpdf/QPDF.hh>
#include <qpdf/QPDFObjectHandle.hh>
#include <qpdf/QPDFWriter.hh>
#include <qpdf/Pl_Buffer.hh>
#include <cstring>
#include <exception>
#include <span>
#include <stdexcept>
#include <string>
#include <string_view>
#include <vector>

namespace {

struct SaveParams {
    std::string_view xmp;
    std::span<const char*> pairs;
    int metadata_count;
};

class CallbackPipeline final : public Pipeline {
public:
    using WriteFunc = int (*)(void*, const void*, size_t);

    CallbackPipeline(WriteFunc fn, void* ctx)
        : Pipeline("callback", nullptr), m_fn(fn), m_ctx(ctx) {}

    void write(unsigned char const* data, size_t len) override {
        if (m_fn(m_ctx, data, len) != 1) {
            throw std::runtime_error("write callback failed");
        }
    }

    void finish() override {}

private:
    WriteFunc m_fn;
    void* m_ctx;
};

[[nodiscard]] static int inject_metadata(QPDF& qpdf, const SaveParams& params) noexcept {
    if (qpdf.isEncrypted()) {
        return -3;
    }

    if (!params.xmp.empty()) {
        try {
            QPDFObjectHandle root = qpdf.getRoot();
            QPDFObjectHandle xmp_stream =
                QPDFObjectHandle::newStream(&qpdf, std::string(params.xmp));
            QPDFObjectHandle xmp_dict = xmp_stream.getDict();
            xmp_dict.replaceKey("/Type", QPDFObjectHandle::newName("/Metadata"));
            xmp_dict.replaceKey("/Subtype", QPDFObjectHandle::newName("/XML"));
            root.replaceKey("/Metadata", xmp_stream);
        } catch (...) {
            return -5;
        }
    }

    if (params.metadata_count > 0 && !params.pairs.empty()) {
        try {
            QPDFObjectHandle info = qpdf.getTrailer().getKey("/Info");
            if (info.isNull()) {
                info = qpdf.makeIndirectObject(QPDFObjectHandle::newDictionary());
                qpdf.getTrailer().replaceKey("/Info", info);
            }

            std::string qkey;
            for (int i = 0; i < params.metadata_count; ++i) {
                const char* key = params.pairs[static_cast<size_t>(i) * 2];
                const char* val = params.pairs[static_cast<size_t>(i) * 2 + 1];
                if (!key || !val) {
                    continue;
                }

                qkey.clear();
                if (key[0] != '/') {
                    qkey.reserve(std::strlen(key) + 1);
                    qkey.push_back('/');
                }
                qkey += key;

                info.replaceKey(qkey, QPDFObjectHandle::newUnicodeString(val));
            }
        } catch (...) {
            return -5;
        }
    }

    return 0;
}

static void configure_writer(QPDFWriter& writer) {
    writer.setPreserveUnreferencedObjects(false);
    writer.setLinearization(false);
    writer.setObjectStreamMode(qpdf_o_generate);
    writer.setCompressStreams(true);
}

} // namespace

extern "C" {

/**
 * pdfium4j_save_with_metadata_native
 * 
 * Uses QPDF to save a PDF with injected XMP and Info dictionary updates.
 * Handles all structural PDF integrity (XRef, Object Streams, etc.).
 * 
 * Returns:
 *   0  on success
 *  -1  invalid parameters
 *  -2  failed to open source
 *  -3  encrypted PDF (unsupported for write)
 *  -4  failed to parse PDF
 *  -5  failed to inject metadata
 *  -6  failed to write output
 */
SHIM_EXPORT int FPDF_CALLCONV pdfium4j_save_with_metadata_native(
    const char* src_path,
    const char* dst_path,
    const char* xmp_metadata,
    int xmp_len,
    const char** metadata_pairs,
    int metadata_count
) {
    if (!src_path || !dst_path) return -1;
    if (metadata_count < 0) return -1;
    if (metadata_count > 0 && !metadata_pairs) return -1;

    std::span<const char*> pairs(
        metadata_pairs ? metadata_pairs : nullptr,
        static_cast<size_t>(metadata_count) * 2);
    std::string_view xmp =
        (xmp_metadata && xmp_len > 0)
            ? std::string_view(xmp_metadata, static_cast<size_t>(xmp_len))
            : std::string_view();

    SaveParams params{xmp, pairs, metadata_count};

    try {
        QPDF qpdf;
        try {
            qpdf.processFile(src_path);
        } catch (const std::exception&) {
            return -2;
        }

        if (int rc = inject_metadata(qpdf, params); rc != 0) {
            return rc;
        }

        try {
            QPDFWriter writer(qpdf, dst_path);
            configure_writer(writer);
            writer.write();
        } catch (...) {
            return -6;
        }

        return 0;
    } catch (...) {
        return -4;
    }
}

// These legacy functions are now stubs or mapped to the new logic if possible.
// However, the Java side will be updated to call pdfium4j_save_with_metadata_native directly.

SHIM_EXPORT int FPDF_CALLCONV pdfium4j_save_incremental(FPDF_DOCUMENT doc, const char* out_path) {
    // PDFium's incremental save is broken for our needs. 
    // We prefer a full rewrite via QPDF to ensure integrity.
    return -1; 
}

SHIM_EXPORT int FPDF_CALLCONV pdfium4j_save_copy(FPDF_DOCUMENT doc, const char* out_path) {
    return -1;
}

SHIM_EXPORT int FPDF_CALLCONV pdfium4j_set_meta_utf8(FPDF_DOCUMENT doc, const char* key, const char* value_utf8) {
    // This is now handled during the save call via QPDF.
    // Returning 0 to avoid breaking legacy Java code that might still call it before save.
    return 0; 
}

/**
 * pdfium4j_save_with_metadata_mem_native
 * 
 * Version that works entirely in memory or via callbacks to avoid temp files.
 */
SHIM_EXPORT int FPDF_CALLCONV pdfium4j_save_with_metadata_mem_native(
    const void* src_buf,
    size_t      src_len,
    int (*write_block)(void* pThis, const void* pData, size_t size),
    void* pThis,
    const char* xmp_metadata,
    int xmp_len,
    const char** metadata_pairs,
    int metadata_count
) {
    if (!src_buf || src_len == 0 || !write_block) return -1;
    if (metadata_count < 0) return -1;
    if (metadata_count > 0 && !metadata_pairs) return -1;

    std::span<const char*> pairs(
        metadata_pairs ? metadata_pairs : nullptr,
        static_cast<size_t>(metadata_count) * 2);
    std::string_view xmp =
        (xmp_metadata && xmp_len > 0)
            ? std::string_view(xmp_metadata, static_cast<size_t>(xmp_len))
            : std::string_view();

    SaveParams params{xmp, pairs, metadata_count};

    try {
        QPDF qpdf;
        try {
            qpdf.processMemoryFile("memory", static_cast<const char*>(src_buf), src_len);
        } catch (const std::exception&) {
            return -2;
        }

        if (int rc = inject_metadata(qpdf, params); rc != 0) {
            return rc;
        }

        try {
            CallbackPipeline cp(write_block, pThis);
            QPDFWriter writer(qpdf);
            configure_writer(writer);
            writer.setOutputPipeline(&cp);
            writer.write();
        } catch (...) {
            return -6;
        }

        return 0;
    } catch (...) {
        return -4;
    }
}

SHIM_EXPORT int FPDF_CALLCONV pdfium4j_save_with_metadata_mem_to_file_native(
    const void* src_buf,
    size_t      src_len,
    const char* dst_path,
    const char* xmp_metadata,
    int xmp_len,
    const char** metadata_pairs,
    int metadata_count
) {
    if (!src_buf || src_len == 0 || !dst_path) return -1;
    if (metadata_count < 0) return -1;
    if (metadata_count > 0 && !metadata_pairs) return -1;

    std::span<const char*> pairs(
        metadata_pairs ? metadata_pairs : nullptr,
        static_cast<size_t>(metadata_count) * 2);
    std::string_view xmp =
        (xmp_metadata && xmp_len > 0)
            ? std::string_view(xmp_metadata, static_cast<size_t>(xmp_len))
            : std::string_view();

    SaveParams params{xmp, pairs, metadata_count};

    try {
        QPDF qpdf;
        try {
            qpdf.processMemoryFile("memory", static_cast<const char*>(src_buf), src_len);
        } catch (const std::exception&) {
            return -2;
        }

        if (int rc = inject_metadata(qpdf, params); rc != 0) {
            return rc;
        }

        try {
            QPDFWriter writer(qpdf, dst_path);
            configure_writer(writer);
            writer.write();
        } catch (...) {
            return -6;
        }

        return 0;
    } catch (...) {
        return -4;
    }
}

} // extern "C"
