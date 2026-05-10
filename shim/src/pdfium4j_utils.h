#pragma once
#include <string>
#include <vector>
#include <cstdint>

namespace pdfium4j {

std::vector<char16_t> utf8_to_utf16(const std::string& utf8);
std::string utf16_to_utf8(const uint16_t* utf16, size_t length);
std::string trim(std::string s);

} // namespace pdfium4j
