package com.dipdv.shared.module.exception;

import lombok.Getter;

@Getter
public class ModuleNotEnabledException extends RuntimeException {
    private final String moduleCode;

    public ModuleNotEnabledException(String moduleCode) {
        super("Módulo não habilitado: " + moduleCode);
        this.moduleCode = moduleCode;
    }
}
