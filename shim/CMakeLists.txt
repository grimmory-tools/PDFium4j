cmake_minimum_required(VERSION 3.15)
project(pdfium4j_shim VERSION 1.0.0 LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON CACHE BOOL "" FORCE)

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

add_subdirectory(vendor/qpdf EXCLUDE_FROM_ALL)

include_directories(${PDFIUM_ROOT}/include)
include_directories(include)
include_directories(vendor/pugixml)
include_directories(vendor/qpdf/include)
include_directories(vendor/qpdf/libqpdf)

if(WIN32)
    # Check environment variables for vcpkg or other paths
    if(DEFINED ENV{ZLIB_ROOT})
        set(ZLIB_ROOT "$ENV{ZLIB_ROOT}")
    endif()
    if(DEFINED ENV{JPEG_ROOT})
        set(JPEG_ROOT "$ENV{JPEG_ROOT}")
    endif()

    find_package(ZLIB)
    find_package(JPEG)
    
    if(NOT ZLIB_FOUND)
        message(STATUS "ZLIB not found via find_package, trying manual search in ${ZLIB_ROOT}")
        find_path(ZLIB_INCLUDE_DIR zlib.h PATHS ${ZLIB_ROOT}/include NO_DEFAULT_PATH)
        find_library(ZLIB_LIBRARY NAMES z zlib zdll PATHS ${ZLIB_ROOT}/lib NO_DEFAULT_PATH)
        if(ZLIB_INCLUDE_DIR AND ZLIB_LIBRARY)
            set(ZLIB_FOUND ON)
            add_library(ZLIB::ZLIB UNKNOWN IMPORTED)
            set_target_properties(ZLIB::ZLIB PROPERTIES
                INTERFACE_INCLUDE_DIRECTORIES "${ZLIB_INCLUDE_DIR}"
                IMPORTED_LOCATION "${ZLIB_LIBRARY}")
        endif()
    endif()

    if(NOT JPEG_FOUND)
        message(STATUS "JPEG not found via find_package, trying manual search in ${JPEG_ROOT}")
        find_path(JPEG_INCLUDE_DIR jpeglib.h PATHS ${JPEG_ROOT}/include NO_DEFAULT_PATH)
        find_library(JPEG_LIBRARY NAMES jpeg libjpeg jpeg-static PATHS ${JPEG_ROOT}/lib NO_DEFAULT_PATH)
        if(JPEG_INCLUDE_DIR AND JPEG_LIBRARY)
            set(JPEG_FOUND ON)
            add_library(JPEG::JPEG UNKNOWN IMPORTED)
            set_target_properties(JPEG::JPEG PROPERTIES
                INTERFACE_INCLUDE_DIRECTORIES "${JPEG_INCLUDE_DIR}"
                IMPORTED_LOCATION "${JPEG_LIBRARY}")
        endif()
    endif()
else()
    find_package(ZLIB REQUIRED)
    find_package(JPEG REQUIRED)
endif()

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
