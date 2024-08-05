#include <stdio.h>
#include <stdint.h>
#include <stdbool.h>
#include <stdlib.h>
#include <time.h>

#define MEM_BASE 0x100000000          // Base address of the memory region
#define REGION_SIZE (1 * 1024 * 1024) // Size of the memory region (1MB)
#define TEST_COUNT 10                 // Number of test iterations
#define ALIGNMENT (64 * 1024)         // 64KB alignment

// Function to write an 8-bit value to a specified memory address
void write_reg_u8(uintptr_t addr, uint8_t value) {
    volatile uint8_t *loc_addr = (volatile uint8_t *)addr;
    *loc_addr = value;
}

// Function to read an 8-bit value from a specified memory address
uint8_t read_reg_u8(uintptr_t addr) {
    return *(volatile uint8_t *)addr;
}

// Function to write a 64-bit value to a specified memory address
void write_reg_u64(uintptr_t addr, uint64_t value) {
    volatile uint64_t *loc_addr = (volatile uint64_t *)addr;
    *loc_addr = value;
}

// Function to read a 64-bit value from a specified memory address
uint64_t read_reg_u64(uintptr_t addr) {
    return *(volatile uint64_t *)addr;
}

// Function to validate that the written value matches the read value
bool validate(uint64_t written, uint64_t read) {
    return written == read;
}

int main(int argc, char **argv) {
    uint64_t val, ret_val; 
    uintptr_t addr;
    int i;

    // Initialize the random number generator with the current time as the seed
    srand(time(NULL));

    // Loop to perform the test TEST_COUNT times
    for (i = 0; i < TEST_COUNT; i++) {
        // Generate a random address within the specified memory region aligned to 64KB boundaries
        addr = MEM_BASE + ((rand() % (REGION_SIZE / ALIGNMENT)) * ALIGNMENT);

        // Generate a random value to write
        val = ((uint64_t)rand() << 32) | rand();

        // Write the random value to the generated address
        printf("[DEBUG] Writing 0x%lx to 0x%lx\n", val, addr);
        write_reg_u64(addr, val);

        // Read the value back from the same address
        ret_val = read_reg_u64(addr);
        printf("[DEBUG] Read 0x%lx from 0x%lx\n", ret_val, addr);

        // Validate that the written value matches the read value
        if (!validate(val, ret_val)) {
            // If validation fails, print an error message and terminate the program
            printf("[ERROR] Validation failed at 0x%lx: wrote 0x%lx, read 0x%lx\n", addr, val, ret_val);
            return -1;
        } else {
            // If validation succeeds, print a success message
            printf("[DEBUG] Validation succeeded at 0x%lx\n", addr);
        }
    }

    // Print a final success message after all tests have passed
    printf("[DEBUG] All writes and reads validated successfully.\n");

    return 0;
}

