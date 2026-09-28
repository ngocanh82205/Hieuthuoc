package com.hieuthuoc.entity;

/** Một đơn vị bán của sản phẩm (id = 0 là đơn vị gốc). factor = số đơn vị gốc trong 1 đơn vị này. */
public record UnitOption(Long id, String name, int factor, long price) {
}
