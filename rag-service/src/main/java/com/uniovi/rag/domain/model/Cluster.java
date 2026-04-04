package com.uniovi.rag.domain.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Generic cluster for grouping similar items (e.g. paragraphs, filter results by document).
 */
public class Cluster<T> {
    private final List<T> items = new ArrayList<>();

    public Cluster(T initialItem) {
        items.add(initialItem);
    }

    public void addItem(T item) {
        items.add(item);
    }

    public int getSize() {
        return items.size();
    }

    public List<T> getItems() {
        return new ArrayList<>(items);
    }

    public T getRepresentativeItem() {
        return items.get(0);
    }

    @Override
    public String toString() {
        return String.format("Cluster[%d items, representative: %s]",
                items.size(), getRepresentativeItem() != null ? getRepresentativeItem().toString() : "null");
    }
}
