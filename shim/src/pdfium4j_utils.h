#pragma once
#include <string>
#include <vector>
#include <cstdint>
#include <memory>
#include <expected>
#include <span>
#include <string_view>
#include "pdfium4j_shim.h"

namespace pdfium4j {

// ABI Safety checks
static_assert(sizeof(pdfium4j_char_info_t) == 24, "pdfium4j_char_info_t size mismatch");
static_assert(alignof(pdfium4j_char_info_t) == 4, "pdfium4j_char_info_t alignment mismatch");

// RAII Deleters
struct DocumentDeleter { void operator()(FPDF_DOCUMENT d) const { if (d) FPDF_CloseDocument(d); } };
struct PageDeleter { void operator()(FPDF_PAGE p) const { if (p) FPDF_ClosePage(p); } };
struct TextPageDeleter { void operator()(FPDF_TEXTPAGE t) const { if (t) FPDFText_ClosePage(t); } };
struct StructTreeDeleter { void operator()(FPDF_STRUCTTREE s) const { if (s) FPDF_StructTree_Close(s); } };
struct BookmarkDeleter { void operator()(FPDF_BOOKMARK b) const { /* Bookmarks are not closed individually */ } };

// RAII Wrappers
using ScopedDocument = std::unique_ptr<struct FPDF_DOCUMENT_T, DocumentDeleter>;
using ScopedPage = std::unique_ptr<struct FPDF_PAGE_T, PageDeleter>;
using ScopedTextPage = std::unique_ptr<struct FPDF_TEXT_PAGE_T, TextPageDeleter>;
using ScopedStructTree = std::unique_ptr<struct FPDF_STRUCT_TREE_T, StructTreeDeleter>;

std::vector<char16_t> utf8_to_utf16(const std::string& utf8);
std::string utf16_to_utf8(const uint16_t* utf16, size_t length);
std::string trim(std::string s);

} // namespace pdfium4j
