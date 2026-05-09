#include "pdfium4j_shim.h"
#include <fpdf_save.h>
#include <stdio.h>

extern "C" {

struct FileWrite : public FPDF_FILEWRITE {
    FILE* file;
    
    FileWrite(FILE* f) : file(f) {
        version = 1;
        WriteBlock = &StaticWriteBlock;
    }
    
    static int StaticWriteBlock(FPDF_FILEWRITE* pThis, const void* pData, unsigned long size) {
        FileWrite* self = static_cast<FileWrite*>(pThis);
        size_t written = fwrite(pData, 1, size, self->file);
        return written == size ? 1 : 0;
    }
};

int pdfium4j_save_incremental(FPDF_DOCUMENT doc, const char* out_path) {
    if (!doc || !out_path) return -1;
    
    FILE* f = fopen(out_path, "ab"); // Open for appending to support incremental
    if (!f) return -1;
    
    FileWrite writer(f);
    int ok = FPDF_SaveAsCopy(doc, &writer, FPDF_INCREMENTAL);
    int close_ok = fclose(f);
    
    return (ok && close_ok == 0) ? 0 : -1;
}

int pdfium4j_save_copy(FPDF_DOCUMENT doc, const char* out_path) {
    if (!doc || !out_path) return -1;
    
    FILE* f = fopen(out_path, "wb");
    if (!f) return -1;
    
    FileWrite writer(f);
    int ok = FPDF_SaveAsCopy(doc, &writer, 0);
    int close_ok = fclose(f);
    
    return (ok && close_ok == 0) ? 0 : -1;
}

}
