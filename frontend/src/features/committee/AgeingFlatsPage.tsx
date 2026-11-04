import { useQuery } from "@tanstack/react-query";
import { useParams, useSearchParams } from "react-router-dom";
import { api } from "../../lib/api";
import { formatDate, formatMonth } from "../../lib/format";
import { AgeingFlats } from "../../lib/types";
import { Card } from "../../components/Card";
import { Money } from "../../components/Money";
import { Page } from "../../components/Page";
import { EmptyState, ErrorState, Skeleton } from "../../components/States";
import { currentMonth } from "./CommitteePage";

export function AgeingFlatsPage() {
  const { bucket = "" } = useParams();
  const [params] = useSearchParams();
  const month = params.get("month") ?? currentMonth();
  const flats = useQuery({
    queryKey: ["committee", "ageing", bucket, month],
    queryFn: () => api.get<AgeingFlats>(`/api/committee/ageing/${encodeURIComponent(bucket)}?month=${month}`),
  });

  return (
    <Page
      width="wide"
      title={flats.data ? flats.data.label : "Dues ageing"}
      subtitle={
        flats.data
          ? `${flats.data.flats} flats · as at ${formatDate(flats.data.asOf)}`
          : formatMonth(month)
      }
      back={{ to: `/desk/committee?month=${month}`, label: "Committee report" }}
    >
      {flats.isPending ? (
        <Skeleton className="h-64 w-full" />
      ) : flats.isError ? (
        <ErrorState error={flats.error} onRetry={() => flats.refetch()} />
      ) : flats.data.items.length === 0 ? (
        <EmptyState title="No flats in this group" body="Choose another age group from the committee report." />
      ) : (
        <Card className="overflow-x-auto">
          <table className="w-full min-w-[480px] text-sm">
            <caption className="sr-only">Flats in this ageing group, most overdue first</caption>
            <thead className="bg-canvas text-xs text-ink-faint">
              <tr>
                <th scope="col" className="px-4 py-2 text-left font-medium">Flat</th>
                <th scope="col" className="px-2 py-2 text-left font-medium">Oldest unpaid due</th>
                <th scope="col" className="px-2 py-2 text-right font-medium">Days overdue</th>
                <th scope="col" className="px-4 py-2 text-right font-medium">Amount</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-line">
              {flats.data.items.map((item) => (
                <tr key={item.flatId}>
                  <td className="px-4 py-3 font-medium tabular-nums text-ink">{item.flatCode}</td>
                  <td className="px-2 py-3 tabular-nums text-ink-soft">{formatDate(item.oldestUnpaidDue)}</td>
                  <td className={`px-2 py-3 text-right tabular-nums ${item.daysOverdue > 0 ? "text-danger" : "text-ink-soft"}`}>
                    {item.daysOverdue}
                  </td>
                  <td className="px-4 py-3 text-right">
                    <Money amount={item.amount} />
                  </td>
                </tr>
              ))}
            </tbody>
            <tfoot>
              <tr className="border-t border-line-strong font-semibold">
                <td className="px-4 py-3" colSpan={3}>
                  Total
                </td>
                <td className="px-4 py-3 text-right">
                  <Money amount={flats.data.amount} />
                </td>
              </tr>
            </tfoot>
          </table>
        </Card>
      )}
    </Page>
  );
}
