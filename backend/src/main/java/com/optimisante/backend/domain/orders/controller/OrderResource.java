package com.optimisante.backend.domain.orders.controller;

import com.optimisante.backend.domain.orders.dto.CheckoutRequestDto;
import com.optimisante.backend.domain.orders.dto.OrderResponseDto;
import com.optimisante.backend.domain.orders.dto.QuoteRequestDto;
import com.optimisante.backend.domain.orders.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderResource {

    private final OrderService orderService;

    @PostMapping("/checkout")
    public ResponseEntity<OrderResponseDto> createCheckoutOrder(@RequestBody CheckoutRequestDto request) {
        return ResponseEntity.ok(orderService.createCheckoutOrder(request));
    }

    @PostMapping("/quote-request")
    public ResponseEntity<OrderResponseDto> createQuoteRequest(@RequestBody QuoteRequestDto request) {
        return ResponseEntity.ok(orderService.createQuoteRequest(request));
    }

    @GetMapping("/my-orders")
    public ResponseEntity<Page<OrderResponseDto>> getMyOrders(@PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(orderService.getMyOrders(pageable));
    }
}
