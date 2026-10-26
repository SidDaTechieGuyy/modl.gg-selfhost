import { useQuery } from '@tanstack/react-query';
import { apiFetch } from '@/lib/api';
import { Card, CardContent } from '@modl-gg/shared-web/components/ui/card';
import { Button } from '@modl-gg/shared-web/components/ui/button';

interface StorageUsage { usedFormatted: string; byType?: Record<string, string | number>; }
export default function UsageSettings() {
  const { data, isLoading, error, refetch } = useQuery<StorageUsage>({
    queryKey: ['/v1/panel/storage/quota'],
    queryFn: async () => {
      const response = await apiFetch('/v1/panel/storage/quota');
      if (!response.ok) throw new Error('Unable to load storage usage.');
      return response.json();
    },
  });
  return <Card><CardContent className="p-6 space-y-4">
    <h3 className="text-lg font-medium">Local storage</h3>
    <p>Uploads are stored on this instance’s persistent upload volume. Available capacity depends on its disk.</p>
    {isLoading && <p role="status">Loading storage usage…</p>}
    {error && <p role="alert">{error.message}</p>}
    {data && <><p>Stored files: <strong>{data.usedFormatted}</strong></p>
      <dl className="space-y-2">{Object.entries(data.byType || {}).map(([type, bytes]) =>
        <div className="flex justify-between" key={type}><dt>{type}</dt><dd>{(Number(bytes) / 1024 / 1024).toFixed(2)} MiB</dd></div>)}</dl></>}
    <Button variant="outline" onClick={() => { void refetch(); }}>Refresh</Button>
  </CardContent></Card>;
}
