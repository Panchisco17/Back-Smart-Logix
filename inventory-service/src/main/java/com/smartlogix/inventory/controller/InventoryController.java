package com.smartlogix.inventory.controller;

import com.smartlogix.inventory.dto.CreateInventoryItemRequest;
import com.smartlogix.inventory.dto.InventoryAvailabilityResponse;
import com.smartlogix.inventory.dto.InventoryItemResponse;
import com.smartlogix.inventory.service.InventoryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/inventory")
@Validated
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @PostMapping("/items")
    public InventoryItemResponse create(@Valid @RequestBody CreateInventoryItemRequest request) {
        return inventoryService.createItem(request);
    }

    @GetMapping("/items")
    public List<InventoryItemResponse> list() {
        return inventoryService.findAll();
    }

    @GetMapping("/items/{sku}")
    public InventoryItemResponse findBySku(@PathVariable String sku) {
        return inventoryService.findBySku(sku);
    }

    @GetMapping("/items/{sku}/availability")
    public InventoryAvailabilityResponse checkAvailability(
            @PathVariable String sku,
            @RequestParam @Min(1) int quantity) {
        return inventoryService.checkAvailability(sku, quantity);
    }

    @PatchMapping("/items/{sku}/reserve")
    public InventoryItemResponse reserve(
            @PathVariable String sku,
            @RequestParam @Min(1) int quantity) {
        return inventoryService.reserve(sku, quantity);
    }

    @PostMapping("/items/{sku}/reserve")
    public InventoryItemResponse reservePost(
            @PathVariable String sku,
            @RequestParam @Min(1) int quantity) {
        return inventoryService.reserve(sku, quantity);
    }

    @PatchMapping("/items/{sku}/release")
    public InventoryItemResponse release(
            @PathVariable String sku,
            @RequestParam @Min(1) int quantity) {
        return inventoryService.release(sku, quantity);
    }

    @PostMapping("/items/{sku}/release")
    public InventoryItemResponse releasePost(
            @PathVariable String sku,
            @RequestParam @Min(1) int quantity) {
        return inventoryService.release(sku, quantity);
    }

    @PatchMapping("/items/{sku}/dispatch")
    public InventoryItemResponse dispatch(
            @PathVariable String sku,
            @RequestParam @Min(1) int quantity) {
        return inventoryService.dispatch(sku, quantity);
    }

    @PostMapping("/items/{sku}/dispatch")
    public InventoryItemResponse dispatchPost(
            @PathVariable String sku,
            @RequestParam @Min(1) int quantity) {
        return inventoryService.dispatch(sku, quantity);
    }
}
