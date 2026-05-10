#ifndef _GNU_SOURCE
#define _GNU_SOURCE
#endif
#include "pdfium4j_shim.h"
#include "pdfium4j_utils.h"
#include <fpdfview.h>
#include <fpdf_doc.h>
#include <fpdf_structtree.h>
#include <fpdf_text.h>
#include <string.h>
#include <stdio.h>
#include <vector>
#include <string>
#include "pugixml.hpp"

#ifdef _WIN32
#include <windows.h>
#define SYMLOOKUP(name) GetProcAddress(GetModuleHandleA("pdfium.dll"), name)
#else
#include <dlfcn.h>
#define SYMLOOKUP(name) dlsym(RTLD_DEFAULT, name)
#endif

typedef unsigned long (FPDF_CALLCONV *Type_FPDF_GetXMPMetadata)(FPDF_DOCUMENT, void*, unsigned long);

extern "C" {

SHIM_EXPORT int FPDF_CALLCONV pdfium4j_page_count(FPDF_DOCUMENT doc) {
    if (!doc) return 0;
    return FPDF_GetPageCount(doc);
}

SHIM_EXPORT int FPDF_CALLCONV pdfium4j_page_label(FPDF_DOCUMENT doc, int page_index, char* buf, int buf_len) {
    if (!doc || page_index < 0) return 0;
    unsigned long length = FPDF_GetPageLabel(doc, page_index, nullptr, 0);
    if (length == 0) return 0;
    if (buf && buf_len >= (int)length) {
        FPDF_GetPageLabel(doc, page_index, buf, buf_len);
    }
    return (int)length;
}

SHIM_EXPORT float FPDF_CALLCONV pdfium4j_page_width(FPDF_DOCUMENT doc, int page_index) {
    double w, h;
    if (FPDF_GetPageSizeByIndex(doc, page_index, &w, &h)) {
        return (float)w;
    }
    return 0.0f;
}

SHIM_EXPORT float FPDF_CALLCONV pdfium4j_page_height(FPDF_DOCUMENT doc, int page_index) {
    double w, h;
    if (FPDF_GetPageSizeByIndex(doc, page_index, &w, &h)) {
        return (float)h;
    }
    return 0.0f;
}

SHIM_EXPORT FPDF_BOOKMARK FPDF_CALLCONV pdfium4j_bookmark_first(FPDF_DOCUMENT doc) {
    return FPDFBookmark_GetFirstChild(doc, nullptr);
}

SHIM_EXPORT FPDF_BOOKMARK FPDF_CALLCONV pdfium4j_bookmark_next(FPDF_DOCUMENT doc, FPDF_BOOKMARK bm) {
    return FPDFBookmark_GetNextSibling(doc, bm);
}

SHIM_EXPORT FPDF_BOOKMARK FPDF_CALLCONV pdfium4j_bookmark_first_child(FPDF_DOCUMENT doc, FPDF_BOOKMARK bm) {
    return FPDFBookmark_GetFirstChild(doc, bm);
}

SHIM_EXPORT int FPDF_CALLCONV pdfium4j_bookmark_title(FPDF_BOOKMARK bm, char* buf, int buf_len) {
    if (!bm) return 0;
    unsigned long length = FPDFBookmark_GetTitle(bm, nullptr, 0);
    if (length == 0) return 0;
    if (buf && buf_len >= (int)length) {
        FPDFBookmark_GetTitle(bm, buf, buf_len);
    }
    return (int)length;
}

SHIM_EXPORT int FPDF_CALLCONV pdfium4j_bookmark_page_index(FPDF_DOCUMENT doc, FPDF_BOOKMARK bm) {
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

SHIM_EXPORT FPDF_STRUCTTREE FPDF_CALLCONV pdfium4j_struct_tree_get(FPDF_PAGE page) {
    return FPDF_StructTree_GetForPage(page);
}

SHIM_EXPORT void FPDF_CALLCONV pdfium4j_struct_tree_close(FPDF_STRUCTTREE tree) {
    FPDF_StructTree_Close(tree);
}

SHIM_EXPORT int FPDF_CALLCONV pdfium4j_struct_tree_count_children(FPDF_STRUCTTREE tree) {
    return FPDF_StructTree_CountChildren(tree);
}

SHIM_EXPORT FPDF_STRUCTELEMENT FPDF_CALLCONV pdfium4j_struct_tree_get_child(FPDF_STRUCTTREE tree, int index) {
    return FPDF_StructTree_GetChildAtIndex(tree, index);
}

SHIM_EXPORT int FPDF_CALLCONV pdfium4j_struct_element_count_children(FPDF_STRUCTELEMENT elem) {
    return FPDF_StructElement_CountChildren(elem);
}

SHIM_EXPORT FPDF_STRUCTELEMENT FPDF_CALLCONV pdfium4j_struct_element_get_child(FPDF_STRUCTELEMENT elem, int index) {
    return FPDF_StructElement_GetChildAtIndex(elem, index);
}

SHIM_EXPORT int FPDF_CALLCONV pdfium4j_struct_element_get_mcid(FPDF_STRUCTELEMENT elem, int index) {
    return FPDF_StructElement_GetMarkedContentIdAtIndex(elem, index);
}

SHIM_EXPORT int FPDF_CALLCONV pdfium4j_struct_element_get_type(FPDF_STRUCTELEMENT elem, char* buf, int buf_len) {
    if (!elem) return 0;
    unsigned long length = FPDF_StructElement_GetType(elem, nullptr, 0);
    if (length == 0) return 0;
    if (buf && buf_len >= (int)length) {
        FPDF_StructElement_GetType(elem, buf, buf_len);
    }
    return (int)length;
}

SHIM_EXPORT int FPDF_CALLCONV pdfium4j_struct_element_get_title(FPDF_STRUCTELEMENT elem, char* buf, int buf_len) {
    if (!elem) return 0;
    unsigned long length = FPDF_StructElement_GetTitle(elem, nullptr, 0);
    if (length == 0) return 0;
    if (buf && buf_len >= (int)length) {
        FPDF_StructElement_GetTitle(elem, buf, buf_len);
    }
    return (int)length;
}

SHIM_EXPORT int FPDF_CALLCONV pdfium4j_struct_element_get_alt_text(FPDF_STRUCTELEMENT elem, char* buf, int buf_len) {
    if (!elem) return 0;
    unsigned long length = FPDF_StructElement_GetAltText(elem, nullptr, 0);
    if (length == 0) return 0;
    if (buf && buf_len >= (int)length) {
        FPDF_StructElement_GetAltText(elem, buf, buf_len);
    }
    return (int)length;
}

SHIM_EXPORT int FPDF_CALLCONV pdfium4j_struct_element_get_actual_text(FPDF_STRUCTELEMENT elem, char* buf, int buf_len) {
    if (!elem) return 0;
    unsigned long length = FPDF_StructElement_GetActualText(elem, nullptr, 0);
    if (length == 0) return 0;
    if (buf && buf_len >= (int)length) {
        FPDF_StructElement_GetActualText(elem, buf, buf_len);
    }
    return (int)length;
}

SHIM_EXPORT int FPDF_CALLCONV pdfium4j_struct_element_get_lang(FPDF_STRUCTELEMENT elem, char* buf, int buf_len) {
    if (!elem) return 0;
    unsigned long length = FPDF_StructElement_GetLang(elem, nullptr, 0);
    if (length == 0) return 0;
    if (buf && buf_len >= (int)length) {
        FPDF_StructElement_GetLang(elem, buf, buf_len);
    }
    return (int)length;
}

SHIM_EXPORT int FPDF_CALLCONV pdfium4j_struct_element_get_attribute_count(FPDF_STRUCTELEMENT elem) {
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

SHIM_EXPORT int FPDF_CALLCONV pdfium4j_text_get_chars_with_bounds(FPDF_TEXTPAGE text_page, int start_index, int count, void* out_data) {
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

SHIM_EXPORT int FPDF_CALLCONV pdfium4j_get_meta_utf8(FPDF_DOCUMENT doc, const char* key, char* buf, int buf_len) {
    if (!doc || !key) return 0;
    
    unsigned long length = FPDF_GetMetaText(doc, key, nullptr, 0);
    if (length <= 2) return 0;
    
    std::vector<unsigned char> utf16(length);
    FPDF_GetMetaText(doc, key, utf16.data(), length);
    
    std::string utf8 = pdfium4j::utf16_to_utf8(reinterpret_cast<const uint16_t*>(utf16.data()), (length / 2) - 1);
    
    int needed = (int)utf8.length() + 1;
    if (buf && buf_len >= needed) {
        memcpy(buf, utf8.c_str(), utf8.length() + 1);
    }
    return needed;
}

SHIM_EXPORT int FPDF_CALLCONV pdfium4j_get_xmp_metadata(FPDF_DOCUMENT doc, char* buf, int buf_len) {
    if (!doc) return 0;

    static auto func = (Type_FPDF_GetXMPMetadata)SYMLOOKUP("FPDF_GetXMPMetadata");
    if (func) {
        unsigned long length = func(doc, nullptr, 0);
        if (length == 0) return 0;
        if (buf && buf_len >= (int)length) {
            func(doc, buf, length);
        }
        return (int)length;
    }
    
    unsigned long length = FPDF_GetMetaText(doc, "xmp", nullptr, 0);
    if (length <= 2) return 0;
    
    std::vector<unsigned char> utf16(length);
    FPDF_GetMetaText(doc, "xmp", utf16.data(), length);
    
    std::string utf8 = pdfium4j::utf16_to_utf8(reinterpret_cast<const uint16_t*>(utf16.data()), (length / 2) - 1);
    
    int needed = (int)utf8.length() + 1;
    if (buf && buf_len >= needed) {
        memcpy(buf, utf8.c_str(), utf8.length() + 1);
    }
    return needed;
}

SHIM_EXPORT int FPDF_CALLCONV pdfium4j_get_custom_xmp(FPDF_DOCUMENT doc, const char* ns_uri, const char* key, char* buf, int buf_len) {
    if (!doc || !ns_uri || !key) return 0;
    
    int xmp_len = pdfium4j_get_xmp_metadata(doc, nullptr, 0);
    if (xmp_len <= 1) return 0;
    
    std::vector<char> raw(xmp_len);
    pdfium4j_get_xmp_metadata(doc, raw.data(), xmp_len);
    
    pugi::xml_document xdoc;
    if (!xdoc.load_buffer(raw.data(), xmp_len)) return 0;
    
    pugi::xpath_variable_set vars;
    vars.add("ns", pugi::xpath_type_string);
    vars.add("key", pugi::xpath_type_string);
    vars.set("ns", ns_uri);
    vars.set("key", key);
    
    pugi::xpath_query q("//*[namespace-uri()=$ns and local-name()=$key]", &vars);
    pugi::xpath_node_set nodes = xdoc.select_nodes(q);
    if (nodes.empty()) return 0;
    
    std::string val = nodes.first().node().child_value();
    int needed = (int)val.length() + 1;
    if (buf && buf_len >= needed) {
        memcpy(buf, val.c_str(), val.length() + 1);
    }
    return needed;
}

SHIM_EXPORT int FPDF_CALLCONV pdfium4j_get_custom_xmp_bag(FPDF_DOCUMENT doc, const char* ns_uri, const char* key, char* buf, int buf_len) {
    if (!doc || !ns_uri || !key) return 0;
    
    int xmp_len = pdfium4j_get_xmp_metadata(doc, nullptr, 0);
    if (xmp_len <= 1) return 0;
    
    std::vector<char> raw(xmp_len);
    pdfium4j_get_xmp_metadata(doc, raw.data(), xmp_len);
    
    pugi::xml_document xdoc;
    if (!xdoc.load_buffer(raw.data(), xmp_len)) return 0;
    
    pugi::xpath_variable_set vars;
    vars.add("ns", pugi::xpath_type_string);
    vars.add("key", pugi::xpath_type_string);
    vars.set("ns", ns_uri);
    vars.set("key", key);
    
    pugi::xpath_query q("//*[namespace-uri()=$ns and local-name()=$key]//*[local-name()='li' and namespace-uri()='http://www.w3.org/1999/02/22-rdf-syntax-ns#']", &vars);
    pugi::xpath_node_set nodes = xdoc.select_nodes(q);

    if (nodes.empty()) {
        return pdfium4j_get_custom_xmp(doc, ns_uri, key, buf, buf_len);
    }
    
    int total_needed = 0;
    for (auto node : nodes) {
        std::string val = node.node().child_value();
        total_needed += (int)val.length() + 1;
    }
    total_needed += 1; // Double null at end
    
    if (buf && buf_len >= total_needed) {
        char* p = buf;
        for (auto node : nodes) {
            std::string val = node.node().child_value();
            memcpy(p, val.c_str(), val.length() + 1);
            p += val.length() + 1;
        }
        *p = '\0';
    }
    return total_needed;
}

} // extern "C"

#include <qpdf/QPDF.hh>
#include <qpdf/QPDFObjectHandle.hh>

extern "C" {

SHIM_EXPORT int FPDF_CALLCONV pdfium4j_read_info_dict(const char* src_path, Pdfium4jMetaCallback cb, void* ud) {
    if (!src_path || !cb) return -1;
    try {
        QPDF qpdf;
        qpdf.processFile(src_path);
        QPDFObjectHandle trailer = qpdf.getTrailer();
        if (!trailer.hasKey("/Info")) return 0;
        
        QPDFObjectHandle info = trailer.getKey("/Info");
        if (!info.isDictionary()) return 0;

        for (auto const& it : info.getDictAsMap()) {
            std::string key = it.first.substr(1); // strip leading slash
            std::string val;
            QPDFObjectHandle obj = it.second;
            try {
                val = obj.getUTF8Value();
            } catch (...) {
                try {
                    val = obj.getStringValue();
                } catch (...) {
                    continue;
                }
            }
            cb(key.c_str(), val.c_str(), ud);
        }
        return 0;
    } catch (...) {
        return -4;
    }
}

SHIM_EXPORT int FPDF_CALLCONV pdfium4j_read_info_dict_mem(const char* data, size_t len, Pdfium4jMetaCallback cb, void* ud) {
    if (!data || len == 0 || !cb) return -1;
    try {
        QPDF qpdf;
        qpdf.processMemoryFile("mem", data, len, "");
        QPDFObjectHandle trailer = qpdf.getTrailer();
        if (!trailer.hasKey("/Info")) return 0;
        
        QPDFObjectHandle info = trailer.getKey("/Info");
        if (!info.isDictionary()) return 0;

        for (auto const& it : info.getDictAsMap()) {
            std::string key = it.first.substr(1); // strip leading slash
            std::string val;
            QPDFObjectHandle obj = it.second;
            try {
                val = obj.getUTF8Value();
            } catch (...) {
                try {
                    val = obj.getStringValue();
                } catch (...) {
                    continue;
                }
            }
            cb(key.c_str(), val.c_str(), ud);
        }
        return 0;
    } catch (...) {
        return -4;
    }
}

} // extern "C"
