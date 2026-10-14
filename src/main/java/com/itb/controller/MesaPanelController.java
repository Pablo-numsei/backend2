package com.itb.controller;

import com.itb.dto.MesaPanelResponse;
import com.itb.service.MesaPanelService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/mesas/painel")
@RequiredArgsConstructor
public class MesaPanelController {
    private final MesaPanelService mesaPanelService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'GARCOM')")
    public List<MesaPanelResponse> getPanel() {
        return mesaPanelService.getPanel();
    }
}
