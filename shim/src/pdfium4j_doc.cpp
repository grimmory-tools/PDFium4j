#include "pdfium4j_shim.h"
#include <fpdfview.h>
#include <fpdf_doc.h>
#include <fpdf_structtree.h>
#include <fpdf_text.h>
#include <string.h>
#include <stdio.h>

extern "C" {

FPDF_EXPORT int FPDF_CALLCONV pdfium4j_page_count(FPDF_DOCUMENT doc) {
    return FPDF_GetPageCount(doc);
}

FPDF_EXPORT int FPDF_CALLCONV pdfium4j_page_label(FPDF_DOCUMENT doc, int page_index, char* buf, int buf_len) {
    if (!doc || page_index < 0) return 0;
    unsigned long length = FPDF_GetPageLabel(doc, page_index, nullptr, 0);
    if (length == 0) return 0;
    if (buf && buf_len >= (int)length) {
        FPDF_GetPageLabel(doc, page_index, buf, buf_len);
    }
    return (int)length;
}

FPDF_EXPORT float FPDF_CALLCONV pdfium4j_page_width(FPDF_DOCUMENT doc, int page_index) {
    double w, h;
    if (FPDF_GetPageSizeByIndex(doc, page_index, &w, &h)) {
        return (float)w;
    }
    return 0.0f;
}

FPDF_EXPORT float FPDF_CALLCONV pdfium4j_page_height(FPDF_DOCUMENT doc, int page_index) {
    double w, h;
    if (FPDF_GetPageSizeByIndex(doc, page_index, &w, &h)) {
        return (float)h;
    }
    return 0.0f;
}

FPDF_EXPORT FPDF_BOOKMARK FPDF_CALLCONV pdfium4j_bookmark_first(FPDF_DOCUMENT doc) {
    return FPDFBookmark_GetFirstChild(doc, nullptr);
}

FPDF_EXPORT FPDF_BOOKMARK FPDF_CALLCONV pdfium4j_bookmark_next(FPDF_DOCUMENT doc, FPDF_BOOKMARK bm) {
    return FPDFBookmark_GetNextSibling(doc, bm);
}

FPDF_EXPORT FPDF_BOOKMARK FPDF_CALLCONV pdfium4j_bookmark_first_child(FPDF_DOCUMENT doc, FPDF_BOOKMARK bm) {
    return FPDFBookmark_GetFirstChild(doc, bm);
}

FPDF_EXPORT int FPDF_CALLCONV pdfium4j_bookmark_title(FPDF_BOOKMARK bm, char* buf, int buf_len) {
    if (!bm) return 0;
    unsigned long length = FPDFBookmark_GetTitle(bm, nullptr, 0);
    if (length == 0) return 0;
    if (buf && buf_len >= (int)length) {
        FPDFBookmark_GetTitle(bm, buf, buf_len);
    }
    return (int)length;
}

FPDF_EXPORT int FPDF_CALLCONV pdfium4j_bookmark_page_index(FPDF_DOCUMENT doc, FPDF_BOOKMARK bm) {
    if (!doc || !bm) return -1;
    FPDF_DEST dest = FPDFBookmark_GetDest(doc, bm);
    if (!dest) {
        FPDF_ACTION action = FPDFBookmark_GetAction(bm);
        if (action) {
            dest = FPDFAction_GetDest(doc, action);
        }
    }
    if (!dest) return -1;
    return FPDFDest_GetDestPageIndex(doc, dest);
}


FPDF_EXPORT FPDF_STRUCTTREE FPDF_CALLCONV pdfium4j_struct_tree_get(FPDF_PAGE page) {
    return FPDF_StructTree_GetForPage(page);
}

FPDF_EXPORT void FPDF_CALLCONV pdfium4j_struct_tree_close(FPDF_STRUCTTREE tree) {
    FPDF_StructTree_Close(tree);
}

FPDF_EXPORT int FPDF_CALLCONV pdfium4j_struct_tree_count_children(FPDF_STRUCTTREE tree) {
    return FPDF_StructTree_CountChildren(tree);
}

FPDF_EXPORT FPDF_STRUCTELEMENT FPDF_CALLCONV pdfium4j_struct_tree_get_child(FPDF_STRUCTTREE tree, int index) {
    return FPDF_StructTree_GetChildAtIndex(tree, index);
}

FPDF_EXPORT int FPDF_CALLCONV pdfium4j_struct_element_count_children(FPDF_STRUCTELEMENT elem) {
    return FPDF_StructElement_CountChildren(elem);
}

FPDF_EXPORT FPDF_STRUCTELEMENT FPDF_CALLCONV pdfium4j_struct_element_get_child(FPDF_STRUCTELEMENT elem, int index) {
    return FPDF_StructElement_GetChildAtIndex(elem, index);
}

FPDF_EXPORT int FPDF_CALLCONV pdfium4j_struct_element_get_mcid(FPDF_STRUCTELEMENT elem, int index) {
    return FPDF_StructElement_GetMarkedContentIdAtIndex(elem, index);
}

FPDF_EXPORT int FPDF_CALLCONV pdfium4j_struct_element_get_type(FPDF_STRUCTELEMENT elem, char* buf, int buf_len) {
    if (!elem) return 0;
    unsigned long length = FPDF_StructElement_GetType(elem, nullptr, 0);
    if (length == 0) return 0;
    if (buf && buf_len >= (int)length) {
        FPDF_StructElement_GetType(elem, buf, buf_len);
    }
    return (int)length;
}

FPDF_EXPORT int FPDF_CALLCONV pdfium4j_struct_element_get_title(FPDF_STRUCTELEMENT elem, char* buf, int buf_len) {
    if (!elem) return 0;
    unsigned long length = FPDF_StructElement_GetTitle(elem, nullptr, 0);
    if (length == 0) return 0;
    if (buf && buf_len >= (int)length) {
        FPDF_StructElement_GetTitle(elem, buf, buf_len);
    }
    return (int)length;
}

FPDF_EXPORT int FPDF_CALLCONV pdfium4j_struct_element_get_alt_text(FPDF_STRUCTELEMENT elem, char* buf, int buf_len) {
    if (!elem) return 0;
    unsigned long length = FPDF_StructElement_GetAltText(elem, nullptr, 0);
    if (length == 0) return 0;
    if (buf && buf_len >= (int)length) {
        FPDF_StructElement_GetAltText(elem, buf, buf_len);
    }
    return (int)length;
}

FPDF_EXPORT int FPDF_CALLCONV pdfium4j_struct_element_get_actual_text(FPDF_STRUCTELEMENT elem, char* buf, int buf_len) {
    if (!elem) return 0;
    unsigned long length = FPDF_StructElement_GetActualText(elem, nullptr, 0);
    if (length == 0) return 0;
    if (buf && buf_len >= (int)length) {
        FPDF_StructElement_GetActualText(elem, buf, buf_len);
    }
    return (int)length;
}

FPDF_EXPORT int FPDF_CALLCONV pdfium4j_struct_element_get_lang(FPDF_STRUCTELEMENT elem, char* buf, int buf_len) {
    if (!elem) return 0;
    unsigned long length = FPDF_StructElement_GetLang(elem, nullptr, 0);
    if (length == 0) return 0;
    if (buf && buf_len >= (int)length) {
        FPDF_StructElement_GetLang(elem, buf, buf_len);
    }
    return (int)length;
}

FPDF_EXPORT int FPDF_CALLCONV pdfium4j_struct_element_get_attribute_count(FPDF_STRUCTELEMENT elem) {
    return FPDF_StructElement_GetAttributeCount(elem);
}


struct CharInfo {
    int charCode;
    float left;
    float bottom;
    float right;
    float top;
    float fontSize;
};

FPDF_EXPORT int FPDF_CALLCONV pdfium4j_text_get_chars_with_bounds(FPDF_TEXTPAGE text_page, int start_index, int count, void* out_data) {
    if (!text_page || !out_data || start_index < 0 || count <= 0) return 0;
    
    CharInfo* out = static_cast<CharInfo*>(out_data);
    int actual = 0;
    
    for (int i = 0; i < count; i++) {
        int idx = start_index + i;
        unsigned int unicode = FPDFText_GetUnicode(text_page, idx);
        if (unicode == 0 || unicode == 0xFFFE || unicode == 0xFFFF) continue;
        
        double left, top, right, bottom;
        if (FPDFText_GetCharBox(text_page, idx, &left, &right, &bottom, &top)) {
            out[actual].charCode = (int)unicode;
            out[actual].left = (float)left;
            out[actual].bottom = (float)bottom;
            out[actual].right = (float)right;
            out[actual].top = (float)top;
            out[actual].fontSize = (float)FPDFText_GetFontSize(text_page, idx);
            actual++;
        }
    }
    
    return actual;
}

}
