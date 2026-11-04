import { useQuery } from "@tanstack/react-query";
import { api } from "../../lib/api";
import { Bill, ComplaintDetail, ComplaintSummary, FlatSummary, LedgerLine, Receipt } from "../../lib/types";

export const residentKeys = {
  summary: (flatId: number) => ["flat", flatId, "summary"] as const,
  bills: (flatId: number) => ["flat", flatId, "bills"] as const,
  ledger: (flatId: number) => ["flat", flatId, "ledger"] as const,
  complaints: (flatId: number) => ["flat", flatId, "complaints"] as const,
  complaint: (id: number) => ["complaint", id] as const,
  receipt: (receiptNo: string) => ["receipt", receiptNo] as const,
};

export function useFlatSummary(flatId: number) {
  return useQuery({
    queryKey: residentKeys.summary(flatId),
    queryFn: () => api.get<FlatSummary>(`/api/flats/${flatId}/summary`),
  });
}

export function useBills(flatId: number) {
  return useQuery({
    queryKey: residentKeys.bills(flatId),
    queryFn: () => api.get<Bill[]>(`/api/flats/${flatId}/bills`),
  });
}

export function useLedger(flatId: number) {
  return useQuery({
    queryKey: residentKeys.ledger(flatId),
    queryFn: () => api.get<LedgerLine[]>(`/api/flats/${flatId}/ledger`),
  });
}

export function useFlatComplaints(flatId: number) {
  return useQuery({
    queryKey: residentKeys.complaints(flatId),
    queryFn: () => api.get<ComplaintSummary[]>(`/api/flats/${flatId}/complaints`),
  });
}

export function useComplaint(id: number) {
  return useQuery({
    queryKey: residentKeys.complaint(id),
    queryFn: () => api.get<ComplaintDetail>(`/api/complaints/${id}`),
    refetchInterval: (query) => (query.state.data?.classificationStatus === "PENDING" ? 3000 : false),
  });
}

export function useReceipt(receiptNo: string) {
  return useQuery({
    queryKey: residentKeys.receipt(receiptNo),
    queryFn: () => api.get<Receipt>(`/api/receipts/${encodeURIComponent(receiptNo)}`),
  });
}
