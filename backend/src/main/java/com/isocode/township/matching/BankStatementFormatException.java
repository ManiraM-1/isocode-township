package com.isocode.township.matching;

import com.isocode.township.common.BadRequestException;

public class BankStatementFormatException extends BadRequestException {

    public BankStatementFormatException(String detail) {
        super(detail);
    }
}
