package com.isocode.township.matching;

import java.util.List;

public record ParsedStatement(List<StatementRow> rows, List<RowError> errors) {

    public int totalRows() {
        return rows.size() + errors.size();
    }
}
