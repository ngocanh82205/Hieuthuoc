package com.hieuthuoc.service;

import com.hieuthuoc.entity.Category;
import com.hieuthuoc.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CategoryService {
    private final CategoryRepository repo;

    /** Toàn bộ danh mục theo thứ tự cây (cha trước, con sau), đã điền depth. */
    public List<Category> tree() {
        List<Category> all = repo.findAllByOrderBySortOrderAscNameAsc();
        Set<Long> ids = new HashSet<>();
        for (Category c : all) ids.add(c.getId());
        Map<Long, List<Category>> children = new HashMap<>();
        List<Category> roots = new ArrayList<>();
        for (Category c : all) {
            if (c.getParentId() == null || !ids.contains(c.getParentId())) roots.add(c);
            else children.computeIfAbsent(c.getParentId(), k -> new ArrayList<>()).add(c);
        }
        List<Category> out = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (Category r : roots) walk(r, 0, children, out, seen);
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
    public List<Long> descendantIds(Category root) {
        Set<Long> ids = new LinkedHashSet<>();
        ids.add(root.getId());
        List<Category> all = repo.findAll();
        boolean changed;
        do {
            changed = false;
            for (Category c : all) {
                if (c.getParentId() != null && ids.contains(c.getParentId()) && !ids.contains(c.getId())) {
                    ids.add(c.getId());
                    changed = true;
                }
            }
        } while (changed);
        return new ArrayList<>(ids);
    }

    /** Đường dẫn từ gốc tới danh mục (breadcrumb). */
    public List<Category> path(Category c) {
        LinkedList<Category> path = new LinkedList<>();
        Set<Long> seen = new HashSet<>();
        while (c != null && seen.add(c.getId())) {
            path.addFirst(c);
            c = c.getParent();
        }
        return path;
    }

    public List<Category> children(Category c) {
        return tree().stream().filter(x -> Objects.equals(x.getParentId(), c.getId())).toList();
    }
}
