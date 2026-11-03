package com.isocode.township.payment;

import com.isocode.township.billing.Bill;
import com.isocode.township.billing.BillRepository;
import com.isocode.township.billing.OutstandingLateFees;
import com.isocode.township.payment.PaymentAllocator.ItemType;
import com.isocode.township.payment.PaymentAllocator.OpenItem;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class OpenItemsQuery {

    private final BillRepository billRepository;
    private final OutstandingLateFees outstandingLateFees;

    public OpenItemsQuery(BillRepository billRepository, OutstandingLateFees outstandingLateFees) {
        this.billRepository = billRepository;
        this.outstandingLateFees = outstandingLateFees;
    }

    public List<OpenItem> forFlat(Long flatId) {
        List<OpenItem> items = new ArrayList<>();
        for (Bill bill : billRepository.findUnpaidByFlat(flatId)) {
            items.add(new OpenItem(ItemType.BILL, bill.getId(), bill.getMonth().atDay(1), bill.principalOutstanding()));
        }
        for (OutstandingLateFees.Balance balance : outstandingLateFees.forFlat(flatId)) {
            items.add(new OpenItem(ItemType.LATE_FEE, balance.charge().getId(), balance.charge().getToDate(),
                    balance.outstanding()));
        }
        return items;
    }
}
