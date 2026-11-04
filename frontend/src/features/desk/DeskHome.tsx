import { Page } from "../../components/Page";
import { useMe } from "../../lib/session";

export function DeskHome() {
  const me = useMe();
  return (
    <Page title={`Hello, ${me.data?.name.split(" ")[0] ?? ""}`} width="wide">
      <p className="text-sm text-ink-soft">You are signed in to the help desk.</p>
    </Page>
  );
}
