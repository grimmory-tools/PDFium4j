#ifndef _GNU_SOURCE
#define _GNU_SOURCE
#endif
#include "pdfium4j_shim.h"
#include <fpdfview.h>
#include <fpdf_doc.h>
#include <fpdf_save.h>
#include <string.h>
#include <stdio.h>
#include <cstdint>
#include <vector>
#include <string>
#include <sstream>
#include <algorithm>
#include "pugixml.hpp"

#ifdef _WIN32
#include <windows.h>
#define SYMLOOKUP(name) GetProcAddress(GetModuleHandleA("pdfium.dll"), name)
#else
#include <dlfcn.h>
#define SYMLOOKUP(name) dlsym(RTLD_DEFAULT, name)
#endif

typedef unsigned long (FPDF_CALLCONV *Type_FPDF_GetXMPMetadata)(FPDF_DOCUMENT, void*, unsigned long);
typedef FPDF_BOOL (FPDF_CALLCONV *Type_FPDF_SetMetaText)(FPDF_DOCUMENT, FPDF_BYTESTRING, FPDF_WIDESTRING);

static std::string trim(std::string s) {
    s.erase(s.begin(), std::find_if(s.begin(), s.end(), [](unsigned char ch) {
        return !std::isspace(ch);
    }));
    s.erase(std::find_if(s.rbegin(), s.rend(), [](unsigned char ch) {
        return !std::isspace(ch);
    }).base(), s.end());
    return s;
}

static std::vector<char16_t> utf8_to_utf16(const std::string& utf8) {
    std::vector<char16_t> utf16;
    const unsigned char* p = reinterpret_cast<const unsigned char*>(utf8.c_str());
    while (*p) {
        if (*p < 0x80) {
            utf16.push_back(*p++);
        } else if ((*p & 0xE0) == 0xC0 && (p[1] & 0xC0) == 0x80) {
            char16_t c = (*p++ & 0x1F) << 6;
            c |= (*p++ & 0x3F);
            utf16.push_back(c);
        } else if ((*p & 0xF0) == 0xE0 && (p[1] & 0xC0) == 0x80 && (p[2] & 0xC0) == 0x80) {
            char16_t c = (*p++ & 0x0F) << 12;
            c |= (*p++ & 0x3F) << 6;
            c |= (*p++ & 0x3F);
            utf16.push_back(c);
        } else if ((*p & 0xF8) == 0xF0 && (p[1] & 0xC0) == 0x80 && (p[2] & 0xC0) == 0x80 && (p[3] & 0xC0) == 0x80) {
            uint32_t cp = (*p++ & 0x07) << 18;
            cp |= (*p++ & 0x3F) << 12;
            cp |= (*p++ & 0x3F) << 6;
            cp |= (*p++ & 0x3F);
            cp -= 0x10000;
            utf16.push_back(static_cast<char16_t>(0xD800 | (cp >> 10)));
            utf16.push_back(static_cast<char16_t>(0xDC00 | (cp & 0x3FF)));
        } else {
            p++;
        }
    }
    utf16.push_back(0);
    return utf16;
}

extern "C" {

int pdfium4j_get_meta_utf8(FPDF_DOCUMENT doc, const char* key, char* buf, int buf_len) {
    if (!doc || !key) return 0;
    
    unsigned long length = FPDF_GetMetaText(doc, key, nullptr, 0);
    if (length <= 2) return 0;
    
    std::vector<unsigned char> utf16(length);
    FPDF_GetMetaText(doc, key, utf16.data(), length);
    
    std::string utf8;
    const char16_t* ptr = reinterpret_cast<const char16_t*>(utf16.data());
    size_t char_count = (length / 2) - 1;
    
    for (size_t i = 0; i < char_count; ++i) {
        uint32_t c = ptr[i];
        if (c >= 0xD800 && c <= 0xDBFF && i + 1 < char_count) {
            uint32_t next = ptr[i + 1];
            if (next >= 0xDC00 && next <= 0xDFFF) {
                c = 0x10000 + ((c - 0xD800) << 10) + (next - 0xDC00);
                i++;
            }
        }

        if (c < 0x80) {
            utf8 += (char)c;
        } else if (c < 0x800) {
            utf8 += (char)(0xC0 | (c >> 6));
            utf8 += (char)(0x80 | (c & 0x3F));
        } else if (c < 0x10000) {
            utf8 += (char)(0xE0 | (c >> 12));
            utf8 += (char)(0x80 | ((c >> 6) & 0x3F));
            utf8 += (char)(0x80 | (c & 0x3F));
        } else {
            utf8 += (char)(0xF0 | (c >> 18));
            utf8 += (char)(0x80 | ((c >> 12) & 0x3F));
            utf8 += (char)(0x80 | ((c >> 6) & 0x3F));
            utf8 += (char)(0x80 | (c & 0x3F));
        }
    }
    
    int needed = (int)utf8.length() + 1;
    if (buf && buf_len >= needed) {
        memcpy(buf, utf8.c_str(), utf8.length() + 1);
    }
    return needed;
}

int pdfium4j_set_meta_utf8(FPDF_DOCUMENT doc, const char* key, const char* value_utf8) {
    if (!doc || !key || !value_utf8) return -1;
    
    static auto func = (Type_FPDF_SetMetaText)SYMLOOKUP("FPDF_SetMetaText");
    if (!func) return -1;

    std::vector<char16_t> utf16 = utf8_to_utf16(value_utf8);
    
    if (func(doc, key, reinterpret_cast<FPDF_WIDESTRING>(utf16.data()))) {
        return 0;
    }
    return -1;
}

int pdfium4j_get_xmp_metadata(FPDF_DOCUMENT doc, char* buf, int buf_len) {
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
    
    std::string utf8;
    const char16_t* ptr = reinterpret_cast<const char16_t*>(utf16.data());
    size_t char_count = (length / 2) - 1;
    for (size_t i = 0; i < char_count; ++i) {
        uint32_t c = ptr[i];
        if (c >= 0xD800 && c <= 0xDBFF && i + 1 < char_count) {
            uint32_t next = ptr[i + 1];
            if (next >= 0xDC00 && next <= 0xDFFF) {
                c = 0x10000 + ((c - 0xD800) << 10) + (next - 0xDC00);
                i++;
            }
        }
        if (c < 0x80) utf8 += (char)c;
        else if (c < 0x800) { utf8 += (char)(0xC0 | (c >> 6)); utf8 += (char)(0x80 | (c & 0x3F)); }
        else if (c < 0x10000) { utf8 += (char)(0xE0 | (c >> 12)); utf8 += (char)(0x80 | ((c >> 6) & 0x3F)); utf8 += (char)(0x80 | (c & 0x3F)); }
        else {
            utf8 += (char)(0xF0 | (c >> 18)); utf8 += (char)(0x80 | ((c >> 12) & 0x3F)); utf8 += (char)(0x80 | ((c >> 6) & 0x3F)); utf8 += (char)(0x80 | (c & 0x3F));
        }
    }
    
    int needed = (int)utf8.length() + 1;
    if (buf && buf_len >= needed) {
        memcpy(buf, utf8.c_str(), utf8.length() + 1);
    }
    return needed;
}

int pdfium4j_get_custom_xmp(FPDF_DOCUMENT doc, const char* ns_uri, const char* key, char* buf, int buf_len) {
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

int pdfium4j_get_custom_xmp_bag(FPDF_DOCUMENT doc, const char* ns_uri, const char* key, char* buf, int buf_len) {
    if (!doc || !ns_uri || !key) return 0;
    
    int xmp_len = pdfium4j_get_xmp_metadata(doc, nullptr, 0);
    if (xmp_len <= 1) {
        return 0;
    }
    
    std::vector<char> raw(xmp_len);
    pdfium4j_get_xmp_metadata(doc, raw.data(), xmp_len);
    
    pugi::xml_document xdoc;
    if (!xdoc.load_buffer(raw.data(), xmp_len)) {
        return 0;
    }
    
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

static pugi::xml_node find_or_create_description(pugi::xml_document& xdoc, const char* ns_uri, const char* prefix) {
    pugi::xml_node rdf = xdoc.child("x:xmpmeta").child("rdf:RDF");
    if (!rdf) rdf = xdoc.select_node("//rdf:RDF").node();
    if (!rdf) return pugi::xml_node();

    for (pugi::xml_node desc : rdf.children("rdf:Description")) {
        if (std::string(desc.attribute(("xmlns:" + std::string(prefix)).c_str()).value()) == ns_uri) {
            return desc;
        }
    }
    
    pugi::xml_node desc = rdf.append_child("rdf:Description");
    desc.append_attribute("rdf:about") = "";
    desc.append_attribute(("xmlns:" + std::string(prefix)).c_str()) = ns_uri;
    return desc;
}

extern "C" {

int pdfium4j_set_custom_xmp(FPDF_DOCUMENT doc, const char* ns_uri, const char* prefix, const char* key, const char* value) {
    if (!doc || !ns_uri || !prefix || !key || !value) return -1;
    
    static auto func = (Type_FPDF_SetMetaText)SYMLOOKUP("FPDF_SetMetaText");
    if (!func) return -1;

    int xmp_len = pdfium4j_get_xmp_metadata(doc, nullptr, 0);
    pugi::xml_document xdoc;

    if (xmp_len > 1) {
        std::vector<char> raw(xmp_len);
        pdfium4j_get_xmp_metadata(doc, raw.data(), xmp_len);
        if (!xdoc.load_buffer(raw.data(), xmp_len)) {
            xmp_len = 0; // fall through to skeleton
        }
    }
    if (xmp_len <= 1) {
        const char* skeleton = "<?xpacket begin=\"\xEF\xBB\xBF\" id=\"W5M0MpCehiHzreSzNTczkc9d\"?>\n"
                               "<x:xmpmeta xmlns:x=\"adobe:ns:meta/\">\n"
                               "  <rdf:RDF xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\">\n"
                               "  </rdf:RDF>\n"
                               "</x:xmpmeta>\n"
                               "<?xpacket end=\"w\"?>";
        xdoc.load_string(skeleton);
    }
    
    pugi::xml_node desc = find_or_create_description(xdoc, ns_uri, prefix);
    if (!desc) return -1;
    
    std::string qname = std::string(prefix) + ":" + key;
    pugi::xml_node node = desc.child(qname.c_str());
    if (!node) node = desc.append_child(qname.c_str());
    node.text().set(value);
    
    std::ostringstream oss;
    xdoc.save(oss, "  ");
    std::string out = oss.str();

    
    std::vector<char16_t> utf16 = utf8_to_utf16(out);

    bool ok1 = func(doc, "XMP", reinterpret_cast<FPDF_WIDESTRING>(utf16.data()));
    bool ok2 = func(doc, "xmp", reinterpret_cast<FPDF_WIDESTRING>(utf16.data()));

    return (ok1 || ok2) ? 0 : -1;
}

int pdfium4j_set_custom_xmp_bag(FPDF_DOCUMENT doc, const char* ns_uri, const char* prefix, const char* key, const char* values_joined, const char* bag_type) {
    if (!doc || !ns_uri || !prefix || !key || !values_joined) return -1;
    
    static auto func = (Type_FPDF_SetMetaText)SYMLOOKUP("FPDF_SetMetaText");
    if (!func) return -1;

    int xmp_len = pdfium4j_get_xmp_metadata(doc, nullptr, 0);
    pugi::xml_document xdoc;

    if (xmp_len > 1) {
        std::vector<char> raw(xmp_len);
        pdfium4j_get_xmp_metadata(doc, raw.data(), xmp_len);
        if (!xdoc.load_buffer(raw.data(), xmp_len)) {
            xmp_len = 0; // fall through to skeleton
        }
    }
    if (xmp_len <= 1) {
        const char* skeleton = "<?xpacket begin=\"\xEF\xBB\xBF\" id=\"W5M0MpCehiHzreSzNTczkc9d\"?>\n"
                               "<x:xmpmeta xmlns:x=\"adobe:ns:meta/\">\n"
                               "  <rdf:RDF xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\">\n"
                               "  </rdf:RDF>\n"
                               "</x:xmpmeta>\n"
                               "<?xpacket end=\"w\"?>";
        xdoc.load_string(skeleton);
    }
    
    pugi::xml_node desc = find_or_create_description(xdoc, ns_uri, prefix);
    if (!desc) return -1;
    
    std::string qname = std::string(prefix) + ":" + key;
    pugi::xml_node node = desc.child(qname.c_str());
    if (node) desc.remove_child(node);
    
    node = desc.append_child(qname.c_str());
    std::string btype = "rdf:";
    btype += (bag_type ? bag_type : "Bag");
    pugi::xml_node bag = node.append_child(btype.c_str());
    
    std::stringstream ss(values_joined);
    std::string item;
    while (std::getline(ss, item, ';')) {
        std::string t = trim(item);
        if (!t.empty()) {
            bag.append_child("rdf:li").text().set(t.c_str());
        }
    }
    
    std::ostringstream oss;
    xdoc.save(oss, "  ");
    std::string out = oss.str();

    
    std::vector<char16_t> utf16 = utf8_to_utf16(out);

    bool ok1 = func(doc, "XMP", reinterpret_cast<FPDF_WIDESTRING>(utf16.data()));
    bool ok2 = func(doc, "xmp", reinterpret_cast<FPDF_WIDESTRING>(utf16.data()));

    return (ok1 || ok2) ? 0 : -1;
}

}
