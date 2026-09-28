package com.hieuthuoc.service;

import com.hieuthuoc.entity.Category;
import com.hieuthuoc.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/** Danh mục đa cấp: dựng cây, lấy danh mục con cháu, đường dẫn (breadcrumb). */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CategoryService {
    private final CategoryRepository categoryRepo;

    /** Toàn bộ danh mục theo thứ tự cây (cha trước, con sau), đã điền depth. */
    public List<Category> tree() {
        List<Category> all = categoryRepo.findAllByOrderBySortOrderAscNameAsc();
        Map<Long, List<Category>> children = new HashMap<>();
        List<Category> roots = new ArrayList<>();
        Set<Long> ids = new HashSet<>();
        all.forEach(c -> ids.add(c.getId()));
        for (Category c : all) {
            if (c.getParent() == null || !ids.contains(c.getParent().getId())) roots.add(c);
            else children.computeIfAbsent(c.getParent().getId(), k -> new ArrayList<>()).add(c);
        }
        List<Category> out = new ArrayList<>();
        for (Category r : roots) walk(r, 0, children, out, new HashSet<>());
        return out;
    }

    private void walk(Category c, int depth, Map<Long, List<Category>> children, List<Category> out, Set<Long> seen) {
        if (!seen.add(c.getId())) return;
        c.setDepth(depth);
        c.setHasChildren(children.containsKey(c.getId()));
        out.add(c);
        for (Category ch : children.getOrDefault(c.getId(), List.of())) walk(ch, depth + 1, children, out, seen);
    }

    /** id của danh mục và tất cả danh mục con cháu. */
    public Set<Long> descendantIds(Category root) {
        Set<Long> ids = new LinkedHashSet<>();
        ids.add(root.getId());
        List<Category> all = categoryRepo.findAll();
        boolean changed = true;
        while (changed) {
            changed = false;
            for (Category c : all) {
                if (c.getParent() != null && ids.contains(c.getParent().getId()) && ids.add(c.getId())) changed = true;
            }
        }
        return ids;
    }

    /** Đường dẫn từ gốc tới danh mục (dùng cho breadcrumb). */
    public List<Category> path(Category c) {
        LinkedList<Category> path = new LinkedList<>();
        Set<Long> seen = new HashSet<>();
        for (Category x = c; x != null && seen.add(x.getId()); x = x.getParent()) path.addFirst(x);
        return path;
    }

    public List<Category> children(Category c) {
        return tree().stream().filter(x -> x.getParent() != null && x.getParent().getId().equals(c.getId())).toList();
    }
}
