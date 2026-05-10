#include "pdfium4j_shim.h"
#include <qpdf/QPDF.hh>
#include <qpdf/QPDFWriter.hh>
#include <qpdf/QPDFObjectHandle.hh>
#include <qpdf/Pl_Buffer.hh>
#include <string>
#include <vector>
#include <iostream>

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

    try {
        QPDF qpdf;
        try {
            qpdf.processFile(src_path);
        } catch (...) {
            return -2; // Failed to open/parse source
        }

        if (qpdf.isEncrypted()) {
            return -3; // Reject encrypted writes
        }

        // 1. Inject XMP Metadata if provided
        if (xmp_metadata && xmp_len > 0) {
            try {
                QPDFObjectHandle root = qpdf.getRoot();
                QPDFObjectHandle xmp_stream = QPDFObjectHandle::newStream(&qpdf, std::string(xmp_metadata, xmp_len));
                QPDFObjectHandle xmp_dict = xmp_stream.getDict();
                xmp_dict.replaceKey("/Type", QPDFObjectHandle::newName("/Metadata"));
                xmp_dict.replaceKey("/Subtype", QPDFObjectHandle::newName("/XML"));
                root.replaceKey("/Metadata", xmp_stream);
            } catch (...) {
                return -5; // Failed to inject XMP
            }
        }

        // 2. Update Info Dictionary if provided
        if (metadata_pairs && metadata_count > 0) {
            try {
                QPDFObjectHandle info = qpdf.getTrailer().getKey("/Info");
                if (info.isNull()) {
                    info = qpdf.makeIndirectObject(QPDFObjectHandle::newDictionary());
                    qpdf.getTrailer().replaceKey("/Info", info);
                }

                for (int i = 0; i < metadata_count; i++) {
                    const char* key = metadata_pairs[i * 2];
                    const char* val = metadata_pairs[i * 2 + 1];
                    if (key && val) {
                        std::string qkey = (key[0] == '/') ? key : ("/" + std::string(key));
                        info.replaceKey(qkey, QPDFObjectHandle::newUnicodeString(val));
                    }
                }
            } catch (...) {
                return -5; // Failed to inject Info metadata
            }
        }

        // 3. Write Output
        try {
            QPDFWriter writer(qpdf, dst_path);
            writer.setPreserveUnreferencedObjects(false);
            writer.write();
        } catch (...) {
            return -6; // Failed to write output
        }

        return 0;
    } catch (...) {
        return -4; // General error
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

    try {
        QPDF qpdf;
        try {
            qpdf.processMemoryFile("memory", static_cast<const char*>(src_buf), src_len);
        } catch (...) {
            return -2;
        }

        if (qpdf.isEncrypted()) {
            return -3;
        }

        // 1. Inject XMP
        if (xmp_metadata && xmp_len > 0) {
            try {
                QPDFObjectHandle root = qpdf.getRoot();
                QPDFObjectHandle xmp_stream = QPDFObjectHandle::newStream(&qpdf, std::string(xmp_metadata, xmp_len));
                QPDFObjectHandle xmp_dict = xmp_stream.getDict();
                xmp_dict.replaceKey("/Type", QPDFObjectHandle::newName("/Metadata"));
                xmp_dict.replaceKey("/Subtype", QPDFObjectHandle::newName("/XML"));
                root.replaceKey("/Metadata", xmp_stream);
            } catch (...) {
                return -5;
            }
        }

        // 2. Inject Info
        if (metadata_pairs && metadata_count > 0) {
            try {
                QPDFObjectHandle info = qpdf.getTrailer().getKey("/Info");
                if (info.isNull()) {
                    info = qpdf.makeIndirectObject(QPDFObjectHandle::newDictionary());
                    qpdf.getTrailer().replaceKey("/Info", info);
                }

                for (int i = 0; i < metadata_count; i++) {
                    const char* key = metadata_pairs[i * 2];
                    const char* val = metadata_pairs[i * 2 + 1];
                    if (key && val) {
                        std::string qkey = (key[0] == '/') ? key : ("/" + std::string(key));
                        info.replaceKey(qkey, QPDFObjectHandle::newUnicodeString(val));
                    }
                }
            } catch (...) {
                return -5;
            }
        }

        // 3. Write via callback
        try {
            class CallbackPipeline : public Pipeline {
            public:
                CallbackPipeline(int (*write_block)(void*, const void*, size_t), void* pThis)
                    : Pipeline("callback", nullptr), write_block(write_block), pThis(pThis) {}
                virtual ~CallbackPipeline() {}
                virtual void write(unsigned char const* data, size_t len) override {
                    if (write_block(pThis, data, len) != 1) {
                        throw std::runtime_error("write failed");
                    }
                }
                virtual void finish() override {}
            private:
                int (*write_block)(void*, const void*, size_t);
                void* pThis;
            };

            CallbackPipeline cp(write_block, pThis);
            QPDFWriter writer(qpdf);
            writer.setPreserveUnreferencedObjects(false);
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

} // extern "C"
