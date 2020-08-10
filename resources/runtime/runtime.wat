(import "wasi_unstable" "fd_write" (func $fd_write (param i32 i32 i32 i32) (result i32)))

(memory 1)

(export "memory" (memory 0))

;; cyanc_insert_heap_start_here

(func $cy_array_get (param $arr_ptr i32) (param $arr_idx i32) (result i32)
    (block $B0
        get_local $arr_idx
        get_local $arr_ptr
        i32.load
        i32.lt_u
        br_if $B0
        unreachable
    )
    local.get $arr_idx
    i32.const 1
    i32.add
    i32.const 4
    i32.mul
    local.get $arr_ptr
    i32.add
    i32.load
)

(func $cy_init_heap
    (local $curr_block_ptr i32)
    (local $next_block_ptr i32)

    global.get $heap_start
    local.set $curr_block_ptr

    loop $init_blocks
         ;; set block free
         local.get $curr_block_ptr
         i32.const 0
         i32.store8

         ;; calculate next block addr
         local.get $curr_block_ptr
         i32.const 64
         i32.add
         local.set $next_block_ptr

         ;; set next block addr in curr block
         local.get $curr_block_ptr
         i32.const 1
         i32.add
         local.get $next_block_ptr
         i32.store

         ;; if we are not at end, do again
         i32.const 1024
         local.get $curr_block_ptr
         i32.gt_u
         if $continue
            local.get $next_block_ptr
            local.set $curr_block_ptr
            br $init_blocks
        end
    end
)

(func $cy_malloc (result i32)
    (local $block_ptr i32)

    global.get $heap_start
    local.set $block_ptr

    loop $search (result i32)
        ;; find if block is free
        local.get $block_ptr

        ;; break if block is not free
        i32.load8_s
        i32.const 0
        i32.ne
        if $not_free
            ;; read ptr to next block
            i32.const 1
            local.get $block_ptr
            i32.add
            i32.load

            local.set $block_ptr

            br $search
        end

        ;; return block if it is free

        local.get $block_ptr
        i32.const 1
        i32.store8

        local.get $block_ptr
        i32.const 5
        i32.add
    end
)

(func $cy_dump_mem
    i32.const 0
    i32.const 0
    i32.store
    i32.const 4
    i32.const 512
    i32.store
    (call $print (i32.const 0))
)

(func $print (param i32)
    (call $fd_write
        (i32.const 1)
        (local.get 0)
        (i32.const 1)
        (i32.const 20)
    )

    drop
)

;; cyanc_insert_here

;; cyanc_insert_prealloc_here
