package com.umar.ecommerce.inventory.service;

import com.umar.ecommerce.inventory.dto.response.PageResponse;
import com.umar.ecommerce.inventory.dto.response.StockAdjustmentResponse;
import com.umar.ecommerce.inventory.dto.response.StockItemResponse;
import com.umar.ecommerce.inventory.entity.StockItem;
import com.umar.ecommerce.inventory.exception.InventoryProblem;
import com.umar.ecommerce.inventory.repository.StockAdjustmentRepository;
import com.umar.ecommerce.inventory.repository.StockItemRepository;
import com.umar.ecommerce.inventory.repository.StockItemSpecifications;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

@Service
public class StockQueryService {

    private static final Set<String> STOCK_SORTS = Set.of("sku", "onHand", "createdAt", "updatedAt");
    private static final Set<String> HISTORY_SORTS = Set.of("createdAt");

    private final StockItemRepository stocks;
    private final StockAdjustmentRepository adjustments;
    private final InventoryMapper mapper;

    public StockQueryService(
            StockItemRepository stocks,
            StockAdjustmentRepository adjustments,
            InventoryMapper mapper
    ) {
        this.stocks = stocks;
        this.adjustments = adjustments;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public PageResponse<StockItemResponse> list(String search, int page, int size, String sort) {
        InventoryPaging.validate(page, size);
        InventoryPaging.SortSelection selection = InventoryPaging.parse(sort, STOCK_SORTS, "sku,asc");
        var result = stocks.findAll(
                StockItemSpecifications.search(search),
                PageRequest.of(page, size, InventoryPaging.toSort(selection))
        );
        return PageResponse.from(result.map(mapper::toStockItem), selection.contract());
    }

    @Transactional(readOnly = true)
    public StockItemResponse get(UUID id) {
        return mapper.toStockItem(require(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<StockAdjustmentResponse> history(UUID id, int page, int size, String sort) {
        require(id);
        InventoryPaging.validate(page, size);
        InventoryPaging.SortSelection selection = InventoryPaging.parse(sort, HISTORY_SORTS, "createdAt,desc");
        var result = adjustments.findByStockItemId(
                id,
                PageRequest.of(page, size, InventoryPaging.historySort(selection))
        );
        return PageResponse.from(result.map(mapper::toAdjustment), selection.contract());
    }

    private StockItem require(UUID id) {
        return stocks.findById(id).orElseThrow(() -> new InventoryProblem(
                HttpStatus.NOT_FOUND,
                InventoryProblem.NOT_FOUND,
                "Stock item was not found"
        ));
    }
}
