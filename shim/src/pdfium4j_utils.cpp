#include "pdfium4j_utils.h"
#include <algorithm>
#include <cctype>
#include <cstring>

namespace pdfium4j {

std::vector<char16_t> utf8_to_utf16(const std::string& utf8) {
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

std::string utf16_to_utf8(const uint16_t* ptr, size_t char_count) {
    std::string utf8;
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
    return utf8;
}

std::string trim(std::string s) {
    s.erase(s.begin(), std::find_if(s.begin(), s.end(), [](unsigned char ch) {
        return !std::isspace(ch);
    }));
    s.erase(std::find_if(s.rbegin(), s.rend(), [](unsigned char ch) {
        return !std::isspace(ch);
    }).base(), s.end());
    return s;
}

} // namespace pdfium4j
