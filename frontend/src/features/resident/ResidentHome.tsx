import { Page } from "../../components/Page";
import { useResidentFlat } from "./ResidentLayout";

export function ResidentHome() {
  const { me, flatCode } = useResidentFlat();
  return (
    <Page title={`Hello, ${me.name.split(" ")[0]}`} subtitle={flatCode}>
      <p className="text-sm text-ink-soft">You are signed in.</p>
    </Page>
  );
}
