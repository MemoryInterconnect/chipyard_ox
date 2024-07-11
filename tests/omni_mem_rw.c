#include <stdio.h>
#include <stdint.h>

/*#define MEM_BASE     0x800000
 */
#define MEM_BASE     0x100000000
#define MEM_REG_BASE 0x9028000
#define MEM_LOC_REG_BASE 0x9026000
#define DADDR0 0x8000001232FFFFFB
#define DADDR1 0x8000001232FFFFF9
#define SADDR 0x8000001232FFFF18
#define MEMPOOLADDR0 0x2000000
#define MEMPOOLADDR1 0x40000000

void write_reg_u8(uintptr_t addr, uint8_t value) {
    volatile uint8_t *loc_addr = (volatile uint8_t *)addr;
    *loc_addr = value;
}

uint8_t read_reg_u8(uintptr_t addr) {
    return *(volatile uint8_t *)addr;
}

void write_reg_u64(uintptr_t addr, uint64_t value) {
    volatile uint64_t *loc_addr = (volatile uint64_t *)addr;
    *loc_addr = value;
}

uint64_t read_reg_u64(uintptr_t addr) {
    return *(volatile uint64_t *)addr;
}

int main(int argc, char ** argv) {

	uint64_t val, ret_val;

	val = 3054;

/*
	for(int i = 0; i < 1; i++) {
	//printf("[DEBUG]Start Write , 0x%lu, 0x%lu \n", MEM_BASE, val);
	printf("[DEBUG]Start Write , 0x%lx, 0x%lx \n", MEM_BASE + i*0x1000, val*i*28);
	write_reg_u64(MEM_BASE + i * 0x1000, val*i*28);
	printf("[DEBUG]End Write \n");
	printf("[DEBUG]Start Read , 0x%x\n", MEM_BASE);
	ret_val = read_reg_u64(MEM_BASE + i * 0x1000);
	printf("[DEBUG]Read 0x%lu\n", ret_val);
	}
*/

	printf("[DEBUG]Start Write , 0x%8lx, 0x%4lx \n", MEM_BASE, val);
	write_reg_u8(MEM_BASE, val);
	printf("[DEBUG]End Write \n");

	printf("[DEBUG]Start Read , 0x%lx\n", MEM_BASE);
	ret_val = read_reg_u8(MEM_BASE);
	printf("[DEBUG]Read 0x%x\n", ret_val);

/*
	printf("[DEBUG]Start Write , 0x%8lx, 0x%4lx \n", MEM_BASE+0x20000, val+0x1);
	write_reg_u8(MEM_BASE+0x20000, val+0x1);
	printf("[DEBUG]End Write \n");
	printf("[DEBUG]Start Read , 0x%x\n", MEM_BASE);
	ret_val = read_reg_u8(MEM_BASE+0x20000);
	printf("[DEBUG]Read 0x%x\n", ret_val);
*/
/*
	printf("[DEBUG]Start Write , 0x%8lx, 0x%4lx \n", MEM_BASE+0x40000, val+0x2);
	write_reg_u8(MEM_BASE+0x40000, val+0x2);
	printf("[DEBUG]End Write \n");
	printf("[DEBUG]Start Read , 0x%x\n", MEM_BASE);
	ret_val = read_reg_u8(MEM_BASE+0x40000);
	printf("[DEBUG]Read 0x%x\n", ret_val);

	printf("[DEBUG]Start Write , 0x%8lx, 0x%4lx \n", MEM_BASE+0x60000, val+0x3);
	write_reg_u8(MEM_BASE+0x60000, val+0x3);
	printf("[DEBUG]End Write \n");
	printf("[DEBUG]Start Read , 0x%x\n", MEM_BASE);
	ret_val = read_reg_u8(MEM_BASE+0x60000);
	printf("[DEBUG]Read 0x%x\n", ret_val);

	printf("[DEBUG]Start Write , 0x%8lx, 0x%4lx \n", MEM_BASE+0x80000, val+0x4);
	write_reg_u8(MEM_BASE+0x80000, val+0x4);
	printf("[DEBUG]End Write \n");

	printf("[DEBUG]Start Write , 0x%8lx, 0x%4lx \n", MEM_BASE+0xA0000, val+0x5);
	write_reg_u8(MEM_BASE+0xA0000, val+0x5);
	printf("[DEBUG]End Write \n");

	printf("[DEBUG]Start Write , 0x%8lx, 0x%4lx \n", MEM_BASE+0xC0000, val+0x6);
	write_reg_u8(MEM_BASE+0xC0000, val+0x6);
	printf("[DEBUG]End Write \n");

	printf("[DEBUG]Start Read , 0x%x\n", MEM_BASE);
	ret_val = read_reg_u8(MEM_BASE);
	printf("[DEBUG]Read 0x%x\n", ret_val);
*/
/*	
//-----------------------------
//   Desination MAC Address 0
	printf("[DEBUG]Write Register MAC0 DEST_ADDR 0 0x%8lx, 0x%4x\n", DADDR0, MEM_REG_BASE);
	write_reg_u64(MEM_REG_BASE, DADDR0);
	ret_val = read_reg_u64(MEM_REG_BASE);
	printf("[DEBUG]Read REgister DEST_ADDR 0: 0x%8lx\n", ret_val);

//-----------------------------
//   Desination MAC Address 1
	printf("[DEBUG]Write Register MAC1 DEST_ADDR 1 0x%8lx, 0x%4x\n", DADDR1, MEM_REG_BASE+0x10);
	write_reg_u64(MEM_REG_BASE+0x10, DADDR1);
	ret_val = read_reg_u64(MEM_REG_BASE+0x10);
	printf("[DEBUG]Read REgister DEST_ADDR 1 : 0x%8lx\n", ret_val);

//-----------------------------
//   Source MAC Address 
	printf("[DEBUG]Write Register SRC_ADDR 0x%8lx, 0x%4x\n", SADDR, MEM_REG_BASE+0x20);
	write_reg_u64(MEM_REG_BASE+0x20, SADDR);
	ret_val = read_reg_u64(MEM_REG_BASE + 0x20);
	printf("[DEBUG]Read REgister SRC_ADDR : 0x%8lx\n", ret_val);

//-----------------------------
//   Memory Pool Division Address 
	printf("[DEBUG]Write Register MEM_POOL 0x%8x, 0x%4x\n", MEMPOOLADDR0, MEM_REG_BASE+0x30);
	write_reg_u64(MEM_REG_BASE+0x30, MEMPOOLADDR0);
	ret_val = read_reg_u64(MEM_REG_BASE + 0x30);
	printf("[DEBUG]Read REgister MEM_POLL : 0x%8lx\n", ret_val);

//-----------------------------
//   Check SELECT_OMNI 
	printf("[DEBUG]SELECT_OMNI \n");
	printf("[DEBUG]Start Write , 0x%8lx, 0x%4lx \n", MEM_BASE+0x8000010, val+0x2);
	write_reg_u64(MEM_BASE+0x8000010, val+0x2);
	printf("[DEBUG]End Write \n");
	printf("[DEBUG]Start Read , 0x%8lx\n", MEM_BASE+0x8000010);
	ret_val = read_reg_u64(MEM_BASE+0x8000010);
	printf("[DEBUG]Read 0x%8lx\n", ret_val);

	printf("[DEBUG]SELECT_OMNI \n");
	printf("[DEBUG]Start Write , 0x%8lx, 0x%4lx \n", MEM_BASE+0x10000, val+0x1);
	write_reg_u64(MEM_BASE+0x10000, val+0x1);
	printf("[DEBUG]End Write \n");
	printf("[DEBUG]Start Read , 0x%8lx\n", MEM_BASE+0x10000);
	ret_val = read_reg_u64(MEM_BASE+0x10000);
	printf("[DEBUG]Read 0x%8lx\n", ret_val);
*/
/*
//-----------------------------
//   Desination MAC Address 0
	printf("[DEBUG]Write Register MAD0 DEST_ADDR 1 0x%8lx, 0x%4lx\n", DADDR1, MEM_REG_BASE);
	write_reg_u64(MEM_REG_BASE, DADDR1);
	ret_val = read_reg_u64(MEM_REG_BASE);
	printf("[DEBUG]Read REgister DEST_ADDR 0: 0x%8lx\n", ret_val);

//-----------------------------
//   Desination MAC Address 1
	printf("[DEBUG]Write Register MAC1 DEST_ADDR 0 0x%8lx, 0x%4lx\n", DADDR0, MEM_REG_BASE+0x10);
	write_reg_u64(MEM_REG_BASE+0x10, DADDR0);
	ret_val = read_reg_u64(MEM_REG_BASE+0x10);
	printf("[DEBUG]Read REgister DEST_ADDR 1 : 0x%8lx\n", ret_val);

//-----------------------------
//   Source MAC Address 
	printf("[DEBUG]Write Register SRC_ADDR 0x%8lx, 0x%4lx\n", SADDR, MEM_REG_BASE+0x20);
	write_reg_u64(MEM_REG_BASE+0x20, SADDR);
	ret_val = read_reg_u64(MEM_REG_BASE + 0x20);
	printf("[DEBUG]Read REgister SRC_ADDR : 0x%8lx\n", ret_val);

//-----------------------------
//   Memory Pool Division Address 
	printf("[DEBUG]Write Register MEM_POOL 0x%8lx, 0x%4lx\n", MEMPOOLADDR1, MEM_REG_BASE+0x30);
	write_reg_u64(MEM_REG_BASE+0x30, MEMPOOLADDR1);
	ret_val = read_reg_u64(MEM_REG_BASE + 0x30);
	printf("[DEBUG]Read REgister MEM_POLL : 0x%8x\n", ret_val);

//-----------------------------
//   Check SELECT_OMNI 
	printf("[DEBUG]SELECT_OMNI \n");
	printf("[DEBUG]Start Write , 0x%8lx, 0x%4lx \n", MEM_BASE+0x80000010, val+0x1);
	write_reg_u8(MEM_BASE+0x80000010, val+0x1);
	printf("[DEBUG]End Write \n");
	printf("[DEBUG]Start Read , 0x%8lx\n", MEM_BASE+0x80000010);
	ret_val = read_reg_u8(MEM_BASE+0x80000010);
	printf("[DEBUG]Read 0x%8lx\n", ret_val);

	printf("[DEBUG]SELECT_OMNI \n");
	printf("[DEBUG]Start Write , 0x%8lx, 0x%4lx \n", MEM_BASE+0x30000000, val+0x2);
	write_reg_u8(MEM_BASE+0x30000000, val+0x2);
	printf("[DEBUG]End Write \n");
	printf("[DEBUG]Start Read , 0x%8lx\n", MEM_BASE+30000000);
	ret_val = read_reg_u8(MEM_BASE+0x30000000);
	printf("[DEBUG]Read 0x%8lx\n", ret_val);
*/
//-----------------------------
//   Log 
/*
	printf("[DEBUG]Local Read Register Hit , 0x%4x\n", MEM_LOC_REG_BASE);
	ret_val = read_reg_u64(MEM_LOC_REG_BASE);
	printf("[DEBUG]Local Read REgister Hit 0x%8lx\n", ret_val);
*/
	return 0;
}
