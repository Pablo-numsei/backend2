package com.itb.controller;

import com.itb.dto.MesaCreateRequest;
import com.itb.dto.MesaQrResponse;
import com.itb.dto.MesaResponse;
import com.itb.dto.MesaUpdateRequest;
import com.itb.dto.ManualTableContextRequest;
import com.itb.dto.TableContextRequest;
import com.itb.dto.TableContextResponse;
import com.itb.service.ClienteAuthService;
import com.itb.service.MesaService;
import com.itb.service.TableAccessService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/mesas")
@RequiredArgsConstructor
public class MesaController {
    private final MesaService mesaService;
    private final ClienteAuthService clienteAuthService;
    private final TableAccessService tableAccessService;

    @GetMapping
    public List<MesaResponse> getAll() {
        return mesaService.listActive();
    }

    @GetMapping("/{id}")
    public MesaResponse getById(@PathVariable Long id) {
        return mesaService.getActive(id);
    }

    @GetMapping("/{id}/qr")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'GARCOM')")
    public ResponseEntity<MesaQrResponse> getQr(@PathVariable Long id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(tableAccessService.generateQr(id));
    }

    @PostMapping("/contexto")
    public ResponseEntity<TableContextResponse> getContext(@Valid @RequestBody TableContextRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(tableAccessService.exchangeQr(request.qrToken()));
    }

    @PostMapping("/contexto-manual")
    public ResponseEntity<TableContextResponse> getManualContext(
            @Valid @RequestBody ManualTableContextRequest request,
            @RequestHeader(value = "X-Customer-Token", required = false) String customerToken) {
        clienteAuthService.requireCustomer(customerToken);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(tableAccessService.exchangeByNumber(request.numeroMesa()));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'GARCOM')")
    public ResponseEntity<MesaResponse> create(@Valid @RequestBody MesaCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(mesaService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public MesaResponse update(@PathVariable Long id, @Valid @RequestBody MesaUpdateRequest request) {
        return mesaService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<Void> deactivate(@PathVariable Long id) {
        mesaService.deactivate(id);
        return ResponseEntity.noContent().build();
    }
}
