#include "sentencepiece_wrapper.h"

int* sentencepiece_errno(void) {
    return &errno;
}
