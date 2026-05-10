#ifndef PDFIUM4J_SHIM_H
#define PDFIUM4J_SHIM_H

#include <fpdfview.h>
#include <fpdf_doc.h>
#include <fpdf_save.h>
#include <fpdf_structtree.h>
#include <fpdf_text.h>
#include <stddef.h>

#if defined(_WIN32)
    #ifdef PDFIUM4J_SHIM_IMPLEMENTATION
        #define SHIM_EXPORT __declspec(dllexport)
    #else
        #define SHIM_EXPORT __declspec(dllimport)
    #endif
#else
    #define SHIM_EXPORT __attribute__((visibility("default")))
#endif

#ifdef __cplusplus
extern "C" {
#endif

typedef struct {
    int charCode;
    float left;
    float bottom;
    float right;
    float top;
    float fontSize;
} pdfium4j_char_info_t;

SHIM_EXPORT int FPDF_CALLCONV pdfium4j_page_count(FPDF_DOCUMENT doc);

SHIM_EXPORT int FPDF_CALLCONV pdfium4j_get_meta_utf8(FPDF_DOCUMENT doc, const char* key, char* buf, int buf_len);

SHIM_EXPORT int FPDF_CALLCONV pdfium4j_set_meta_utf8(FPDF_DOCUMENT doc, const char* key, const char* value_utf8);

SHIM_EXPORT int FPDF_CALLCONV pdfium4j_set_custom_xmp(FPDF_DOCUMENT doc,
                            const char* namespace_uri,
                            const char* prefix,
                            const char* key,
                            const char* value_utf8);

SHIM_EXPORT int FPDF_CALLCONV pdfium4j_get_custom_xmp(FPDF_DOCUMENT doc,
                            const char* namespace_uri,
                            const char* key,
                            char* buf,
                            int   buf_len);

SHIM_EXPORT int FPDF_CALLCONV pdfium4j_get_custom_xmp_bag(FPDF_DOCUMENT doc,
                                const char* namespace_uri,
                                const char* key,
                                char* buf,
                                int   buf_len);

SHIM_EXPORT int FPDF_CALLCONV pdfium4j_set_custom_xmp_bag(FPDF_DOCUMENT doc,
                                const char* namespace_uri,
                                const char* prefix,
                                const char* key,
                                const char* values_joined,
                                const char* bag_type);

SHIM_EXPORT int FPDF_CALLCONV pdfium4j_get_xmp_metadata(FPDF_DOCUMENT doc, char* buf, int buf_len);

SHIM_EXPORT int FPDF_CALLCONV pdfium4j_page_label(FPDF_DOCUMENT doc, int page_index, char* buf, int buf_len);

SHIM_EXPORT float FPDF_CALLCONV pdfium4j_page_width(FPDF_DOCUMENT doc, int page_index);
SHIM_EXPORT float FPDF_CALLCONV pdfium4j_page_height(FPDF_DOCUMENT doc, int page_index);

SHIM_EXPORT FPDF_BOOKMARK FPDF_CALLCONV pdfium4j_bookmark_first(FPDF_DOCUMENT doc);
SHIM_EXPORT FPDF_BOOKMARK FPDF_CALLCONV pdfium4j_bookmark_next(FPDF_DOCUMENT doc, FPDF_BOOKMARK bm);
SHIM_EXPORT FPDF_BOOKMARK FPDF_CALLCONV pdfium4j_bookmark_first_child(FPDF_DOCUMENT doc, FPDF_BOOKMARK bm);
SHIM_EXPORT int           FPDF_CALLCONV pdfium4j_bookmark_title(FPDF_BOOKMARK bm, char* buf, int buf_len);
SHIM_EXPORT int           FPDF_CALLCONV pdfium4j_bookmark_page_index(FPDF_DOCUMENT doc, FPDF_BOOKMARK bm);

SHIM_EXPORT FPDF_STRUCTTREE    FPDF_CALLCONV pdfium4j_struct_tree_get(FPDF_PAGE page);
SHIM_EXPORT void               FPDF_CALLCONV pdfium4j_struct_tree_close(FPDF_STRUCTTREE tree);
SHIM_EXPORT int                FPDF_CALLCONV pdfium4j_struct_tree_count_children(FPDF_STRUCTTREE tree);
SHIM_EXPORT FPDF_STRUCTELEMENT FPDF_CALLCONV pdfium4j_struct_tree_get_child(FPDF_STRUCTTREE tree, int index);

SHIM_EXPORT int                FPDF_CALLCONV pdfium4j_struct_element_count_children(FPDF_STRUCTELEMENT elem);
SHIM_EXPORT FPDF_STRUCTELEMENT FPDF_CALLCONV pdfium4j_struct_element_get_child(FPDF_STRUCTELEMENT elem, int index);
SHIM_EXPORT int                FPDF_CALLCONV pdfium4j_struct_element_get_mcid(FPDF_STRUCTELEMENT elem, int index);

SHIM_EXPORT int                FPDF_CALLCONV pdfium4j_struct_element_get_type(FPDF_STRUCTELEMENT elem, char* buf, int buf_len);
SHIM_EXPORT int                FPDF_CALLCONV pdfium4j_struct_element_get_title(FPDF_STRUCTELEMENT elem, char* buf, int buf_len);
SHIM_EXPORT int                FPDF_CALLCONV pdfium4j_struct_element_get_alt_text(FPDF_STRUCTELEMENT elem, char* buf, int buf_len);
SHIM_EXPORT int                FPDF_CALLCONV pdfium4j_struct_element_get_actual_text(FPDF_STRUCTELEMENT elem, char* buf, int buf_len);
SHIM_EXPORT int                FPDF_CALLCONV pdfium4j_struct_element_get_lang(FPDF_STRUCTELEMENT elem, char* buf, int buf_len);
SHIM_EXPORT int                FPDF_CALLCONV pdfium4j_struct_element_get_attribute_count(FPDF_STRUCTELEMENT elem);

SHIM_EXPORT int FPDF_CALLCONV pdfium4j_text_get_chars_with_bounds(FPDF_TEXTPAGE text_page,
                                                                   int start_index,
                                                                   int count,
                                                                   void* out_data);

SHIM_EXPORT int FPDF_CALLCONV pdfium4j_save_incremental(FPDF_DOCUMENT doc, const char* out_path);
SHIM_EXPORT int FPDF_CALLCONV pdfium4j_save_copy(FPDF_DOCUMENT doc, const char* out_path);

/**
 * pdfium4j_save_with_metadata_native
 * 
 * Uses QPDF to save a PDF with injected XMP and Info dictionary updates.
 * 
 * Parameters:
 *   src_path: Path to the source PDF.
 *   dst_path: Path to save the modified PDF.
 *   xmp_metadata: The raw XMP metadata string (XML) to inject.
 *   xmp_len: The byte length of the xmp_metadata string.
 *   metadata_pairs: Array of key-value pairs for the Info dictionary.
 *   metadata_count: The number of key-value pairs (array must have count * 2 elements).
 */
SHIM_EXPORT int FPDF_CALLCONV pdfium4j_save_with_metadata_native(
    const char* src_path,
    const char* dst_path,
    const char* xmp_metadata,
    int xmp_len,
    const char** metadata_pairs,
    int metadata_count
);

/**
 * pdfium4j_save_with_metadata_mem_native
 * 
 * Memory/Callback version of save_with_metadata.
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
);

SHIM_EXPORT int FPDF_CALLCONV pdfium4j_save_with_metadata_mem_to_file_native(
    const void* src_buf,
    size_t      src_len,
    const char* dst_path,
    const char* xmp_metadata,
    int xmp_len,
    const char** metadata_pairs,
    int metadata_count
);

/**
 * QPDF-based metadata enumeration
 */
typedef int (*Pdfium4jMetaCallback)(const char* key, const char* value_utf8, void* userdata);

SHIM_EXPORT int FPDF_CALLCONV pdfium4j_read_info_dict(
    const char*          src_path,
    Pdfium4jMetaCallback callback,
    void*                userdata
);

SHIM_EXPORT int FPDF_CALLCONV pdfium4j_read_info_dict_mem(
    const char*          data,
    size_t               len,
    Pdfium4jMetaCallback callback,
    void*                userdata
);

SHIM_EXPORT int FPDF_CALLCONV pdfium4j_get_xmp_qpdf(
    const char* src_path,
    char*       buf,
    int         buf_len
);

SHIM_EXPORT int FPDF_CALLCONV pdfium4j_get_xmp_qpdf_mem(
    const char* data,
    size_t      len,
    char*       buf,
    int         buf_len
);

SHIM_EXPORT void FPDF_CALLCONV pdfium4j_resolve_optional_symbols(void);

#ifdef __cplusplus
}
#endif

#endif // PDFIUM4J_SHIM_H
