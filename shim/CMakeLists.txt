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

# --- Save current suffixes ---
set(_saved_find_suffixes "${CMAKE_FIND_LIBRARY_SUFFIXES}")
if(WIN32)
    set(CMAKE_FIND_LIBRARY_SUFFIXES .lib .a)
else()
    set(CMAKE_FIND_LIBRARY_SUFFIXES .a)
endif()

# --- ZLIB ---
find_package(ZLIB QUIET)
if(NOT ZLIB_FOUND)
    find_path(ZLIB_H_PATH zlib.h HINTS ${ZLIB_ROOT} ${ZLIB_ROOT}/include PATHS /usr/include /usr/local/include)
    find_library(ZLIB_LIB_PATH NAMES zlibstatic z zlib-static libz.a zlib.lib HINTS ${ZLIB_ROOT} ${ZLIB_ROOT}/lib PATHS /usr/lib /usr/local/lib)
    if(ZLIB_H_PATH AND ZLIB_LIB_PATH)
        set(ZLIB_FOUND ON)
    endif()
else()
    set(ZLIB_H_PATH "${ZLIB_INCLUDE_DIR}" CACHE PATH "Path to zlib.h" FORCE)
    set(ZLIB_LIB_PATH "${ZLIB_LIBRARY}" CACHE FILEPATH "Path to zlib library" FORCE)
endif()

# --- JPEG ---
find_package(JPEG QUIET)
if(NOT JPEG_FOUND)
    find_path(LIBJPEG_H_PATH jpeglib.h HINTS ${JPEG_ROOT} ${JPEG_ROOT}/include PATHS /usr/include /usr/local/include)
    find_library(LIBJPEG_LIB_PATH NAMES jpeg-static jpeg libjpeg.a jpeg.lib HINTS ${JPEG_ROOT} ${JPEG_ROOT}/lib PATHS /usr/lib /usr/local/lib)
    if(LIBJPEG_H_PATH AND LIBJPEG_LIB_PATH)
        set(JPEG_FOUND ON)
    endif()
else()
    set(LIBJPEG_H_PATH "${JPEG_INCLUDE_DIR}" CACHE PATH "Path to jpeglib.h" FORCE)
    set(LIBJPEG_LIB_PATH "${JPEG_LIBRARY}" CACHE FILEPATH "Path to jpeg library" FORCE)
endif()

# --- Restore suffixes ---
set(CMAKE_FIND_LIBRARY_SUFFIXES "${_saved_find_suffixes}")

message(STATUS "ZLIB_FOUND: ${ZLIB_FOUND}")
message(STATUS "  - Header: ${ZLIB_H_PATH}")
message(STATUS "  - Library: ${ZLIB_LIB_PATH}")

message(STATUS "JPEG_FOUND: ${JPEG_FOUND}")
message(STATUS "  - Header: ${LIBJPEG_H_PATH}")
message(STATUS "  - Library: ${LIBJPEG_LIB_PATH}")

# QPDF uses these cache variables
set(ZLIB_H_PATH "${ZLIB_H_PATH}" CACHE PATH "Path to zlib.h" FORCE)
set(ZLIB_LIB_PATH "${ZLIB_LIB_PATH}" CACHE FILEPATH "Path to zlib library" FORCE)
set(LIBJPEG_H_PATH "${LIBJPEG_H_PATH}" CACHE PATH "Path to jpeglib.h" FORCE)
set(LIBJPEG_LIB_PATH "${LIBJPEG_LIB_PATH}" CACHE FILEPATH "Path to jpeg library" FORCE)

# Set standard variables for subdirectories that use find_package
set(ZLIB_INCLUDE_DIR "${ZLIB_H_PATH}" CACHE PATH "" FORCE)
set(ZLIB_LIBRARY "${ZLIB_LIB_PATH}" CACHE FILEPATH "" FORCE)
set(JPEG_INCLUDE_DIR "${LIBJPEG_H_PATH}" CACHE PATH "" FORCE)
set(JPEG_LIBRARY "${LIBJPEG_LIB_PATH}" CACHE FILEPATH "" FORCE)

if(SHIM_IS_MUSL)
    include(FetchContent)
    
    message(STATUS "Hardening: Building ZLIB from source...")
    FetchContent_Declare(
        zlib_source
        URL https://github.com/madler/zlib/archive/refs/tags/v1.3.2.tar.gz
    )
    # ZLIB's CMakeLists is a bit old-school, we need to force PIC
    set(CMAKE_POSITION_INDEPENDENT_CODE ON)
    FetchContent_MakeAvailable(zlib_source)
    set(ZLIB_H_PATH "${zlib_source_SOURCE_DIR}")
    # zlib's target name varies by platform, but 'zlibstatic' is common for static
    if(TARGET zlibstatic)
        set(ZLIB_LIB zlibstatic)
    else()
        set(ZLIB_LIB zlib)
    endif()

    message(STATUS "Hardening: Building LIBJPEG-TURBO from source...")
    FetchContent_Declare(
        libjpeg_turbo
        URL https://github.com/libjpeg-turbo/libjpeg-turbo/archive/refs/tags/3.1.4.1.tar.gz
    )
    set(ENABLE_SHARED OFF CACHE BOOL "" FORCE)
    set(ENABLE_STATIC ON CACHE BOOL "" FORCE)
    set(REQUIRE_SIMD OFF CACHE BOOL "" FORCE) # Disable SIMD to avoid needing NASM in CI
    FetchContent_MakeAvailable(libjpeg_turbo)
    set(LIBJPEG_H_PATH "${libjpeg_turbo_SOURCE_DIR}")
    set(LIBJPEG_LIB jpeg-static)

    # Inform QPDF where to find our just-built libraries
    set(ZLIB_H_PATH "${ZLIB_H_PATH}" CACHE PATH "" FORCE)
    set(ZLIB_LIB_PATH "$<TARGET_FILE:${ZLIB_LIB}>" CACHE FILEPATH "" FORCE)
    set(LIBJPEG_H_PATH "${LIBJPEG_H_PATH}" CACHE PATH "" FORCE)
    set(LIBJPEG_LIB_PATH "$<TARGET_FILE:${LIBJPEG_LIB}>" CACHE FILEPATH "" FORCE)
    
    set(ZLIB_INCLUDE_DIR "${ZLIB_H_PATH}" CACHE PATH "" FORCE)
    set(ZLIB_LIBRARY ${ZLIB_LIB} CACHE FILEPATH "" FORCE)
    set(JPEG_INCLUDE_DIR "${LIBJPEG_H_PATH}" CACHE PATH "" FORCE)
    set(JPEG_LIBRARY ${LIBJPEG_LIB} CACHE FILEPATH "" FORCE)
    
    set(ZLIB_FOUND ON)
    set(JPEG_FOUND ON)
    
    # Define the dummy targets for QPDF to pick up
    if(NOT TARGET z)
        add_library(z INTERFACE GLOBAL)
        target_link_libraries(z INTERFACE ${ZLIB_LIB})
    endif()
    if(NOT TARGET jpeg)
        add_library(jpeg INTERFACE GLOBAL)
        target_link_libraries(jpeg INTERFACE ${LIBJPEG_LIB})
    endif()
endif()

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

if(SHIM_IS_MUSL AND ZLIB_FOUND AND JPEG_FOUND)
    # Force use of absolute paths to static archives to ensure no runtime dependencies
    target_link_libraries(pdfium4j_shim PRIVATE 
        ${PDFIUM_LIB} 
        z 
        jpeg
        libqpdf
    )
elseif(ZLIB_FOUND AND JPEG_FOUND)
    # On non-musl, link normally (likely shared) to avoid -fPIC issues with system static libs
    target_link_libraries(pdfium4j_shim PRIVATE 
        ${PDFIUM_LIB} 
        "${ZLIB_LIB_PATH}" 
        "${LIBJPEG_LIB_PATH}" 
        libqpdf
    )
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
    target_link_options(pdfium4j_shim PRIVATE "LINKER:-rpath,$ORIGIN")
endif()
