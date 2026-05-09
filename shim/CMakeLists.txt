cmake_minimum_required(VERSION 3.15)
project(pdfium4j_shim VERSION 1.0.0 LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Find PDFium headers and library
# These will be provided via Gradle
if(NOT PDFIUM_ROOT)
    message(FATAL_ERROR "PDFIUM_ROOT must be set")
endif()

include_directories(${PDFIUM_ROOT}/include)
include_directories(include)
include_directories(vendor/pugixml)

add_library(pdfium4j_shim SHARED
    src/pdfium4j_meta.cpp
    src/pdfium4j_doc.cpp
    src/pdfium4j_save.cpp
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

target_link_libraries(pdfium4j_shim PRIVATE ${PDFIUM_LIB})

if(NOT WIN32)
    target_link_libraries(pdfium4j_shim PRIVATE ${CMAKE_DL_LIBS})
endif()

# Ensure the output name is consistent
set_target_properties(pdfium4j_shim PROPERTIES
    PREFIX ""
    OUTPUT_NAME "pdfium4j_shim"
)
