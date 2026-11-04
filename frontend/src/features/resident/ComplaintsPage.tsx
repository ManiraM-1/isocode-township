import { ButtonLink } from "../../components/Button";
import { SectionTitle } from "../../components/Card";
import { Page } from "../../components/Page";
import { EmptyState, ErrorState, SkeletonList } from "../../components/States";
import { useFlatComplaints } from "./queries";
import { ComplaintCard } from "./ResidentHome";
import { useResidentFlat } from "./ResidentLayout";

export function ComplaintsPage() {
  const { flatId, flatCode } = useResidentFlat();
  const complaints = useFlatComplaints(flatId);
  const open = complaints.data?.filter((complaint) => complaint.status !== "CLOSED") ?? [];
  const closed = complaints.data?.filter((complaint) => complaint.status === "CLOSED") ?? [];

  return (
    <Page
      title="Complaints"
      subtitle={`Flat ${flatCode}`}
      action={<ButtonLink to="/complaints/new">New complaint</ButtonLink>}
    >
      {complaints.isPending ? (
        <SkeletonList rows={3} />
      ) : complaints.isError ? (
        <ErrorState error={complaints.error} onRetry={() => complaints.refetch()} />
      ) : complaints.data.length === 0 ? (
        <EmptyState
          title="No complaints yet"
          body="Tell the help desk what needs fixing. You can add a photo, and you will see every update here."
          action={<ButtonLink to="/complaints/new" variant="secondary">Raise a complaint</ButtonLink>}
        />
      ) : (
        <>
          <SectionTitle>Open</SectionTitle>
          {open.length === 0 ? (
            <p className="text-sm text-ink-soft">Nothing open right now.</p>
          ) : (
            <ul className="space-y-3">
              {open.map((complaint) => (
                <li key={complaint.id}>
                  <ComplaintCard complaint={complaint} />
                </li>
              ))}
            </ul>
          )}
          {closed.length > 0 && (
            <>
              <SectionTitle>Closed</SectionTitle>
              <ul className="space-y-3">
                {closed.map((complaint) => (
                  <li key={complaint.id}>
                    <ComplaintCard complaint={complaint} />
                  </li>
                ))}
              </ul>
            </>
          )}
        </>
      )}
    </Page>
  );
}
