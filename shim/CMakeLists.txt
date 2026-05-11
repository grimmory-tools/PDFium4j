# PDFium4j Native Shim Build
cmake_minimum_required(VERSION 3.15)
project(pdfium4j_shim VERSION 1.0.1 LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON CACHE BOOL "" FORCE)
if(UNIX AND NOT APPLE)
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -fPIC")
    set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -fPIC")
endif()

# Suppress QPDF PointerHolder transition warnings (and fix MSVC #warning error)
add_definitions(-DPOINTERHOLDER_TRANSITION=0)

# QPDF dependency hints
if(DEFINED ENV{ZLIB_ROOT})
    set(ZLIB_ROOT "$ENV{ZLIB_ROOT}")
    list(APPEND CMAKE_PREFIX_PATH "${ZLIB_ROOT}")
endif()
if(DEFINED ENV{JPEG_ROOT})
    set(JPEG_ROOT "$ENV{JPEG_ROOT}")
    list(APPEND CMAKE_PREFIX_PATH "${JPEG_ROOT}")
endif()

# Find PDFium headers and library
# These will be provided via Gradle
if(NOT PDFIUM_ROOT)
    message(FATAL_ERROR "PDFIUM_ROOT must be set")
endif()

# Build QPDF statically
set(BUILD_SHARED_LIBS OFF)
set(BUILD_STATIC_LIBS ON)
set(BUILD_DOC OFF)
set(INSTALL_MANUAL OFF)
set(INSTALL_EXAMPLES OFF)
set(INSTALL_CMAKE_PACKAGE OFF)
set(INSTALL_PKGCONFIG OFF)
set(ENABLE_QTC OFF)
set(USE_IMPLICIT_CRYPTO OFF)
set(REQUIRE_CRYPTO_NATIVE ON)

# Find ZLIB and JPEG robustly and export to QPDF
find_package(ZLIB QUIET)
if(NOT ZLIB_FOUND)
    find_path(ZLIB_H_PATH zlib.h HINTS ${ZLIB_ROOT} ${ZLIB_ROOT}/include)
    find_library(ZLIB_LIB_PATH NAMES z zlib zdll HINTS ${ZLIB_ROOT} ${ZLIB_ROOT}/lib)
    if(ZLIB_H_PATH AND ZLIB_LIB_PATH)
        set(ZLIB_FOUND ON)
    endif()
else()
    set(ZLIB_H_PATH "${ZLIB_INCLUDE_DIR}" CACHE PATH "Path to zlib.h" FORCE)
    set(ZLIB_LIB_PATH "${ZLIB_LIBRARY}" CACHE FILEPATH "Path to zlib library" FORCE)
endif()

find_package(JPEG QUIET)
if(NOT JPEG_FOUND)
    find_path(LIBJPEG_H_PATH jpeglib.h HINTS ${JPEG_ROOT} ${JPEG_ROOT}/include)
    find_library(LIBJPEG_LIB_PATH NAMES jpeg libjpeg jpeg-static HINTS ${JPEG_ROOT} ${JPEG_ROOT}/lib)
    if(LIBJPEG_H_PATH AND LIBJPEG_LIB_PATH)
        set(JPEG_FOUND ON)
    endif()
else()
    set(LIBJPEG_H_PATH "${JPEG_INCLUDE_DIR}" CACHE PATH "Path to jpeglib.h" FORCE)
    set(LIBJPEG_LIB_PATH "${JPEG_LIBRARY}" CACHE FILEPATH "Path to jpeg library" FORCE)
endif()

message(STATUS "ZLIB_FOUND: ${ZLIB_FOUND}")
message(STATUS "ZLIB_H_PATH: ${ZLIB_H_PATH}")
message(STATUS "ZLIB_LIB_PATH: ${ZLIB_LIB_PATH}")
message(STATUS "JPEG_FOUND: ${JPEG_FOUND}")
message(STATUS "LIBJPEG_H_PATH: ${LIBJPEG_H_PATH}")
message(STATUS "LIBJPEG_LIB_PATH: ${LIBJPEG_LIB_PATH}")

# QPDF uses these cache variables
set(ZLIB_H_PATH "${ZLIB_H_PATH}" CACHE PATH "Path to zlib.h" FORCE)
set(ZLIB_LIB_PATH "${ZLIB_LIB_PATH}" CACHE FILEPATH "Path to zlib library" FORCE)
set(LIBJPEG_H_PATH "${LIBJPEG_H_PATH}" CACHE PATH "Path to jpeglib.h" FORCE)
set(LIBJPEG_LIB_PATH "${LIBJPEG_LIB_PATH}" CACHE FILEPATH "Path to jpeg library" FORCE)

add_subdirectory(vendor/qpdf EXCLUDE_FROM_ALL)

include_directories(${PDFIUM_ROOT}/include)
include_directories(include)
include_directories(vendor/pugixml)
include_directories(vendor/qpdf/include)
include_directories(vendor/qpdf/libqpdf)

add_library(pdfium4j_shim SHARED
    src/pdfium4j_read.cpp
    src/pdfium4j_write.cpp
    src/pdfium4j_utils.cpp
    vendor/pugixml/pugixml.cpp
)

target_compile_definitions(pdfium4j_shim PRIVATE PDFIUM4J_SHIM_IMPLEMENTATION)

find_library(PDFIUM_LIB NAMES pdfium pdfium.dll libpdfium libpdfium.dylib PATHS ${PDFIUM_ROOT} ${PDFIUM_ROOT}/lib ${PDFIUM_ROOT}/bin NO_DEFAULT_PATH)

if(NOT PDFIUM_LIB)
    find_file(PDFIUM_LIB_FILE NAMES libpdfium.dylib libpdfium.so pdfium.dll libpdfium.so.1 PATHS ${PDFIUM_ROOT} ${PDFIUM_ROOT}/lib ${PDFIUM_ROOT}/bin NO_DEFAULT_PATH)
    if(PDFIUM_LIB_FILE)
        set(PDFIUM_LIB "${PDFIUM_LIB_FILE}")
    endif()
endif()

# Last resort: manual path construction for common layouts
if(NOT PDFIUM_LIB)
    if(APPLE AND EXISTS "${PDFIUM_ROOT}/lib/libpdfium.dylib")
        set(PDFIUM_LIB "${PDFIUM_ROOT}/lib/libpdfium.dylib")
    elseif(UNIX AND EXISTS "${PDFIUM_ROOT}/lib/libpdfium.so")
        set(PDFIUM_LIB "${PDFIUM_ROOT}/lib/libpdfium.so")
    elseif(WIN32 AND EXISTS "${PDFIUM_ROOT}/bin/pdfium.dll.lib")
        set(PDFIUM_LIB "${PDFIUM_ROOT}/bin/pdfium.dll.lib")
    endif()
endif()

if(NOT PDFIUM_LIB)
    # Final check: search recursively with glob
    file(GLOB_RECURSE PDFIUM_GLOB "${PDFIUM_ROOT}/*pdfium*")
    foreach(candidate ${PDFIUM_GLOB})
        if(candidate MATCHES "\\.(dylib|so|dll|lib)$")
             if(NOT PDFIUM_LIB)
                 set(PDFIUM_LIB "${candidate}")
             endif()
        endif()
    endforeach()
endif()

if(NOT PDFIUM_LIB)
    message(FATAL_ERROR "Could not find pdfium library in ${PDFIUM_ROOT}")
endif()

if(ZLIB_FOUND AND JPEG_FOUND)
    target_link_libraries(pdfium4j_shim PRIVATE ${PDFIUM_LIB} ZLIB::ZLIB JPEG::JPEG libqpdf)
else()
    # Fallback for Windows if we can't find them but QPDF might find them internally
    target_link_libraries(pdfium4j_shim PRIVATE ${PDFIUM_LIB} libqpdf)
endif()

if(NOT WIN32)
    target_link_libraries(pdfium4j_shim PRIVATE ${CMAKE_DL_LIBS})
endif()

# Ensure the output name is consistent
set_target_properties(pdfium4j_shim PROPERTIES
    PREFIX ""
    OUTPUT_NAME "pdfium4j_shim"
)

if(UNIX AND NOT APPLE)
    set_target_properties(pdfium4j_shim PROPERTIES
        BUILD_RPATH "$ORIGIN"
        INSTALL_RPATH "$ORIGIN"
    )
endif()
