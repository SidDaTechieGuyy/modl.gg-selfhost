import React, { useState, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { HardDrive, Search, Trash2, Download, FolderOpen, Calendar, AlertCircle, Play, Info, Brain } from 'lucide-react';
import { getApiUrl, apiFetch } from '@/lib/api';
import { Button } from '@modl-gg/shared-web/components/ui/button';
import { Input } from '@modl-gg/shared-web/components/ui/input';
import { Label } from '@modl-gg/shared-web/components/ui/label';
import { Card, CardContent, CardHeader, CardTitle } from '@modl-gg/shared-web/components/ui/card';
import { Badge } from '@modl-gg/shared-web/components/ui/badge';
import { Checkbox } from '@modl-gg/shared-web/components/ui/checkbox';
import { Switch } from '@modl-gg/shared-web/components/ui/switch';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@modl-gg/shared-web/components/ui/select';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@modl-gg/shared-web/components/ui/table';
import { AlertDialog, AlertDialogAction, AlertDialogCancel, AlertDialogContent, AlertDialogDescription, AlertDialogFooter, AlertDialogHeader, AlertDialogTitle } from '@modl-gg/shared-web/components/ui/alert-dialog';
import { useToast } from '@modl-gg/shared-web/hooks/use-toast';
import { formatFileSize } from '@/utils/file-utils';
import { errorMessageOr } from '@/utils/errors';
import { useReplayRetentionSettings, useUpdateReplayRetentionSettings } from '@/hooks/use-data';

const DEFAULT_REPLAY_RETENTION_DAYS = 10;

interface StorageFile {
  id: string;
  name: string;
  path: string;
  size: number;
  type: 'ticket' | 'evidence' | 'logs' | 'backup' | 'replay' | 'other';
  createdAt: string;
  lastModified: string;
  url: string;
}

interface StorageFileListing {
  key?: string;
  size?: number;
  lastModified?: string;
  url?: string;
}

interface StorageUsage {
  totalUsed: number;
  byType: Record<string, number>;
}

interface AIUsage {
  usage: { period: string; totalUsed: number; byService: Record<string, number>; trackingAvailable: boolean };
  providerAvailable: boolean;
}

const UsageSettings = () => {
  const { toast } = useToast();
  const { t } = useTranslation();
  const [storageUsage, setStorageUsage] = useState<StorageUsage | null>(null);
  const [aiUsage, setAIUsage] = useState<AIUsage | null>(null);
  const [aiError, setAIError] = useState(false);
  const [files, setFiles] = useState<StorageFile[]>([]);
  const [loading, setLoading] = useState(true);
  const [searchTerm, setSearchTerm] = useState('');
  const [filterType, setFilterType] = useState<string>('all');
  const [selectedFiles, setSelectedFiles] = useState<Set<string>>(new Set());
  const [showDeleteDialog, setShowDeleteDialog] = useState(false);
  const [deleteTarget, setDeleteTarget] = useState<'single' | 'multiple'>('single');
  const [fileToDelete, setFileToDelete] = useState<string>('');
  const [sortBy, setSortBy] = useState<'name' | 'size' | 'date'>('date');
  const [sortOrder, setSortOrder] = useState<'asc' | 'desc'>('desc');
  const [currentPage, setCurrentPage] = useState(1);
  const [itemsPerPage, setItemsPerPage] = useState(25);
  const [replayRetentionEnabled, setReplayRetentionEnabled] = useState(true);
  const [replayRetentionDays, setReplayRetentionDays] = useState(DEFAULT_REPLAY_RETENTION_DAYS);
  const { data: replayRetentionSettings, isLoading: isLoadingReplayRetention, error: replayRetentionError } = useReplayRetentionSettings();
  const updateReplayRetentionSettings = useUpdateReplayRetentionSettings();

  useEffect(() => {
    void fetchStorageData();
    const controller = new AbortController();
    fetch(getApiUrl('/v1/panel/settings/ai-usage'), { credentials: 'include', signal: controller.signal })
      .then(async response => {
        if (!response.ok) throw new Error('Unable to load AI usage');
        setAIUsage(await response.json() as AIUsage);
      }).catch(error => { if (error.name !== 'AbortError') setAIError(true); });
    return () => controller.abort();
  }, []);

  useEffect(() => {
    if (!replayRetentionSettings?.data) {
      return;
    }

    setReplayRetentionEnabled(Boolean(replayRetentionSettings.data.enabled));
    setReplayRetentionDays(Number(replayRetentionSettings.data.days || DEFAULT_REPLAY_RETENTION_DAYS));
  }, [replayRetentionSettings]);

  const fetchStorageData = async () => {
    try {
      setLoading(true);

      const requestOptions = { credentials: 'include' as RequestCredentials };
      const usageResponse = await fetch(getApiUrl('/v1/panel/storage/quota'), requestOptions);
      if (!usageResponse.ok) throw new Error('Unable to load storage usage.');
      const usageData = await usageResponse.json();
      setStorageUsage({
        totalUsed: Number(usageData.usedBytes || 0),
        byType: Object.fromEntries(Object.entries(usageData.byType || {}).map(([type, size]) => [type, Number(size)])),
      });

      // Fetch files
      const filesResponse = await fetch(getApiUrl('/v1/panel/storage/files'), requestOptions);
      if (!filesResponse.ok) {
        throw new Error(`Failed to fetch files: ${filesResponse.status}`);
      }
      const filesData: { files?: StorageFileListing[] } = await filesResponse.json();

      // Transform the file data to match the expected structure
      const transformedFiles = (filesData.files || []).map((file) => {
        // Extract filename from the key (last part after /)
        const parts = file.key?.split('/') || [];
        const filename = parts[parts.length - 1] || 'Unknown';

        // Determine file type based on the folder in the path
        let fileType: StorageFile['type'] = 'other';
        if (file.key?.includes('/evidence/')) fileType = 'evidence';
        else if (file.key?.includes('/tickets/') || file.key?.includes('/ticket/')) fileType = 'ticket';
        else if (file.key?.includes('/logs/')) fileType = 'logs';
        else if (file.key?.includes('/backup/')) fileType = 'backup';
        else if (file.key?.endsWith('.modlreplay') || file.key?.includes('/replays/') || file.key?.includes('/replay/')) fileType = 'replay';

        return {
          id: file.key || `file-${Date.now()}-${Math.random()}`,
          name: filename,
          path: file.key || '',
          size: Number(file.size || 0),
          type: fileType,
          createdAt: file.lastModified || new Date().toISOString(),
          lastModified: file.lastModified || new Date().toISOString(),
          url: file.url || ''
        };
      });

      setFiles(transformedFiles);
      setSelectedFiles(current => new Set([...current].filter(id => transformedFiles.some(file => file.id === id))));
    } catch (error) {
      toast({
        title: t('toast.error'),
        description: t('settings.usage.fetchFailed'),
        variant: "destructive",
      });
    } finally {
      setLoading(false);
    }
  };

  const handleSaveReplayRetention = () => {
    const days = Math.min(365, Math.max(1, Math.floor(Number(replayRetentionDays) || 1)));
    const expectedVersion = Number(replayRetentionSettings?._meta?.version ?? 0);

    updateReplayRetentionSettings.mutate({
      expectedVersion,
      enabled: replayRetentionEnabled,
      days,
    }, {
      onSuccess: (envelope) => {
        if (envelope?.data) {
          setReplayRetentionEnabled(Boolean(envelope.data.enabled));
          setReplayRetentionDays(Number(envelope.data.days || days));
        }

        toast({
          title: t('toast.success'),
          description: t('settings.usage.replayRetentionSaved'),
        });
      },
      onError: (error) => {
        toast({
          title: t('toast.error'),
          description: errorMessageOr(error, t('settings.usage.replayRetentionSaveFailed')),
          variant: "destructive",
        });
      },
    });
  };

  const getTypeColor = (type: string): string => {
    switch (type) {
      case 'ticket': return 'bg-info/20 text-info border-info/30';
      case 'evidence': return 'bg-destructive/20 text-destructive border-destructive/30';
      case 'logs': return 'bg-success/20 text-success border-success/30';
      case 'backup': return 'bg-accent/20 text-accent border-accent/30';
      case 'replay': return 'bg-warning/20 text-warning border-warning/30';
      default: return 'bg-muted text-muted-foreground border-border';
    }
  };

  const getFileType = (filename: string): string => {
    const ext = filename.split('.').pop()?.toLowerCase() || '';
    if (['jpg', 'jpeg', 'png', 'gif', 'webp', 'svg', 'bmp', 'ico'].includes(ext)) return 'Image';
    if (['mp4', 'webm', 'mov', 'avi', 'mkv', 'flv'].includes(ext)) return 'Video';
    if (['mp3', 'wav', 'ogg', 'flac', 'aac'].includes(ext)) return 'Audio';
    if (['pdf'].includes(ext)) return 'PDF';
    if (['doc', 'docx', 'txt', 'rtf', 'odt'].includes(ext)) return 'Document';
    if (['zip', 'rar', '7z', 'tar', 'gz'].includes(ext)) return 'Archive';
    if (['json', 'xml', 'yml', 'yaml', 'csv'].includes(ext)) return 'Data';
    if (['log'].includes(ext)) return 'Log';
    if (['modlreplay'].includes(ext)) return 'Replay';
    return 'File';
  };

  const getFileTypeColor = (fileType: string): string => {
    switch (fileType) {
      case 'Image': return 'bg-pink-100 text-pink-800 dark:bg-pink-900/30 dark:text-pink-300';
      case 'Video': return 'bg-indigo-100 text-indigo-800 dark:bg-indigo-900/30 dark:text-indigo-300';
      case 'Audio': return 'bg-cyan-100 text-cyan-800 dark:bg-cyan-900/30 dark:text-cyan-300';
      case 'PDF': return 'bg-orange-100 text-orange-800 dark:bg-orange-900/30 dark:text-orange-300';
      case 'Document': return 'bg-yellow-100 text-yellow-800 dark:bg-yellow-900/30 dark:text-yellow-300';
      case 'Archive': return 'bg-amber-100 text-amber-800 dark:bg-amber-900/30 dark:text-amber-300';
      case 'Data': return 'bg-teal-100 text-teal-800 dark:bg-teal-900/30 dark:text-teal-300';
      case 'Log': return 'bg-slate-100 text-slate-800 dark:bg-slate-800 dark:text-slate-300';
      case 'Replay': return 'bg-orange-100 text-orange-800 dark:bg-orange-900/30 dark:text-orange-300';
      default: return 'bg-gray-100 text-gray-800 dark:bg-gray-800 dark:text-gray-300';
    }
  };

  const getTypeIcon = (type: string) => {
    switch (type) {
      case 'ticket': return <FolderOpen className="h-4 w-4" />;
      case 'evidence': return <AlertCircle className="h-4 w-4" />;
      case 'logs': return <Calendar className="h-4 w-4" />;
      case 'backup': return <Download className="h-4 w-4" />;
      case 'replay': return <Play className="h-4 w-4" />;
      default: return <HardDrive className="h-4 w-4" />;
    }
  };


  const filteredAndSortedFiles = files
    .filter(file => {
      const matchesSearch = (file.name?.toLowerCase() || '').includes(searchTerm.toLowerCase()) ||
                           (file.path?.toLowerCase() || '').includes(searchTerm.toLowerCase());
      const matchesType = filterType === 'all' || file.type === filterType;
      return matchesSearch && matchesType;
    })
    .sort((a, b) => {
      let comparison = 0;
      switch (sortBy) {
        case 'name':
          comparison = a.name.localeCompare(b.name);
          break;
        case 'size':
          comparison = a.size - b.size;
          break;
        case 'date':
          comparison = new Date(a.lastModified).getTime() - new Date(b.lastModified).getTime();
          break;
      }
      return sortOrder === 'asc' ? comparison : -comparison;
    });

  const totalPages = Math.max(1, Math.ceil(filteredAndSortedFiles.length / itemsPerPage));
  const safePage = Math.min(currentPage, totalPages);
  const paginatedFiles = filteredAndSortedFiles.slice(
    (safePage - 1) * itemsPerPage,
    safePage * itemsPerPage
  );

  useEffect(() => {
    setCurrentPage(1);
  }, [searchTerm, filterType, sortBy, sortOrder]);

  useEffect(() => {
    if (currentPage > totalPages) {
      setCurrentPage(totalPages);
    }
  }, [totalPages, currentPage]);

  const handleSelectFile = (fileId: string) => {
    const newSelected = new Set(selectedFiles);
    if (newSelected.has(fileId)) {
      newSelected.delete(fileId);
    } else {
      newSelected.add(fileId);
    }
    setSelectedFiles(newSelected);
  };

  const handleSelectAll = () => {
    if (selectedFiles.size === filteredAndSortedFiles.length) {
      setSelectedFiles(new Set());
    } else {
      setSelectedFiles(new Set(filteredAndSortedFiles.map(f => f.id)));
    }
  };

  const handleDeleteFile = (fileId: string) => {
    setFileToDelete(fileId);
    setDeleteTarget('single');
    setShowDeleteDialog(true);
  };

  const handleDeleteSelected = () => {
    if (selectedFiles.size === 0) return;
    setDeleteTarget('multiple');
    setShowDeleteDialog(true);
  };

  const confirmDelete = async () => {
    try {
      if (deleteTarget === 'single') {
        const fileToDeleteKey = files.find(f => f.id === fileToDelete)?.path || fileToDelete;
        const deleteResponse = await apiFetch('/v1/panel/storage/bulk-delete', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ keys: [fileToDeleteKey] }),
        });
        if (!deleteResponse.ok) throw new Error('File deletion failed.');
        const result = await deleteResponse.json();
        if (Number(result.deleted ?? 0) !== 1) throw new Error('The file was not deleted. Refresh and try again.');
        toast({
          title: t('toast.success'),
          description: t('settings.usage.fileDeleted'),
        });
      } else {
        // For bulk delete, we need to get the keys for all selected files
        const fileKeys = files
          .filter(f => selectedFiles.has(f.id))
          .map(f => f.path)
          .filter(key => key); // Remove any undefined keys

        const deleteResponse = await apiFetch('/v1/panel/storage/bulk-delete', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ keys: fileKeys }),
        });
        if (!deleteResponse.ok) throw new Error('File deletion failed.');
        const result = await deleteResponse.json();
        if (Number(result.deleted ?? 0) !== fileKeys.length) throw new Error('Some files were not deleted. Refresh and try again.');
        toast({
          title: t('toast.success'),
          description: t('settings.usage.filesDeleted', { count: selectedFiles.size }),
        });
        setSelectedFiles(new Set());
      }

      setShowDeleteDialog(false);
      fetchStorageData();
    } catch (error) {
      toast({
        title: t('toast.error'),
        description: errorMessageOr(error, t('settings.usage.deleteFailed')),
        variant: "destructive",
      });
    }
  };

  if (loading) {
    return (
      <div className="flex items-center justify-center py-12">
        <div className="text-center">
          <div className="animate-spin h-8 w-8 border-b-2 border-primary mx-auto mb-4"></div>
          <p className="text-muted-foreground">{t('settings.usage.loadingStorage')}</p>
        </div>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      {storageUsage && (
        <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-4">
          <Card className="rounded-card shadow-card-inner bg-surface-2">
            <CardHeader><CardTitle className="flex items-center"><HardDrive className="h-5 w-5 mr-2" />{t('settings.usage.storageUsage')}</CardTitle></CardHeader>
            <CardContent><div className="space-y-4">
              <div className="text-sm">{t('settings.usage.used')}: <strong>{formatFileSize(storageUsage.totalUsed)}</strong></div>
              <p className="text-xs text-muted-foreground">Stored locally. Capacity depends on the disk hosting your uploads volume.</p>
              <div className="border-t pt-4 mt-4">
                <div className="text-xs font-medium text-muted-foreground mb-2">{t('settings.usage.storageBreakdown')}</div>
                <div className="space-y-2">{Object.entries(storageUsage.byType).map(([type, size]) => (
                  <div key={type} className="flex items-center justify-between text-sm">
                    <div className="flex items-center space-x-2">{getTypeIcon(type)}<span className="capitalize">{type}</span></div>
                    <span>{formatFileSize(size)}</span>
                  </div>
                ))}</div>
              </div>
            </div></CardContent>
          </Card>
          {/* Restored from the upstream AI Usage card, with self-hosted request counts. */}
          <Card className="rounded-card shadow-card-inner bg-surface-2">
            <CardHeader><CardTitle className="flex items-center">
              <Brain className="h-5 w-5 mr-2" />{t('settings.usage.aiUsage')}
            </CardTitle></CardHeader>
            <CardContent><div className="space-y-4">
              {aiError ? <p role="alert" className="text-sm text-destructive">Unable to load AI usage. Reload to try again.</p>
                : !aiUsage ? <p className="text-sm text-muted-foreground">Loading AI usage…</p> : <>
                  <div>
                    <div className="flex justify-between gap-2 text-sm mb-2">
                      <span>{t('settings.usage.used')}: {Number(aiUsage.usage.totalUsed).toLocaleString()} requests</span>
                      <span className="text-muted-foreground">No instance limit</span>
                    </div>
                    <p className="text-xs text-muted-foreground">Successful requests this UTC month ({aiUsage.usage.period}). Counts start after this update.</p>
                  </div>
                  <div className="border-t pt-4 mt-4 space-y-1">
                    <div className="text-xs font-medium text-muted-foreground">{t('settings.usage.aiBreakdown')}</div>
                    {Object.entries(aiUsage.usage.byService).map(([service, count]) => <div key={service} className="flex justify-between text-xs">
                      <span className="capitalize">{service.replaceAll('_', ' ')}:</span><span>{Number(count).toLocaleString()}</span>
                    </div>)}
                  </div>
                  {!aiUsage.usage.trackingAvailable && <p role="alert" className="text-xs text-destructive">Some usage could not be recorded. Check backend logs.</p>}
                  {!aiUsage.providerAvailable && <p className="text-xs text-muted-foreground">AI provider unavailable. Configure GEMINI_API_KEY to enable AI moderation.</p>}
                </>}
            </div></CardContent>
          </Card>
          <Card className="rounded-card shadow-card-inner bg-surface-2">
            <CardHeader>
              <CardTitle className="flex items-center">
                <Play className="h-5 w-5 mr-2" />
                {t('settings.usage.replayRetention')}
              </CardTitle>
            </CardHeader>
            <CardContent>
              <div className="space-y-4">
                {replayRetentionError && (
                  <div className="bg-destructive/10 border border-destructive/20 text-destructive rounded-lg p-3 text-sm">
                    {t('settings.usage.replayRetentionLoadFailed')}
                  </div>
                )}

                <div className="flex items-center justify-between gap-3">
                  <div>
                    <Label htmlFor="replay-retention-enabled" className="text-sm font-medium">
                      {t('settings.usage.replayRetentionEnabled')}
                    </Label>
                    <p className="text-xs text-muted-foreground mt-1">
                      {t('settings.usage.replayRetentionEnabledDesc')}
                    </p>
                  </div>
                  <Switch
                    id="replay-retention-enabled"
                    checked={replayRetentionEnabled}
                    onCheckedChange={setReplayRetentionEnabled}
                    disabled={isLoadingReplayRetention || updateReplayRetentionSettings.isPending}
                  />
                </div>

                <div className="space-y-2">
                  <Label htmlFor="replay-retention-days">{t('settings.usage.replayRetentionDays')}</Label>
                  <Input
                    id="replay-retention-days"
                    type="number"
                    min="1"
                    max="365"
                    value={replayRetentionDays}
                    onChange={(event) => setReplayRetentionDays(Number(event.target.value))}
                    disabled={!replayRetentionEnabled || isLoadingReplayRetention || updateReplayRetentionSettings.isPending}
                  />
                  <p className="text-xs text-muted-foreground">
                    {replayRetentionEnabled
                      ? t('settings.usage.replayRetentionDaysDesc')
                      : t('settings.usage.replayRetentionDisabledDesc')}
                  </p>
                </div>

                {replayRetentionEnabled && (
                  <div className="flex items-start gap-2 rounded-lg border border-border bg-surface-1 p-3">
                    <Info className="h-4 w-4 shrink-0 text-muted-foreground mt-0.5" />
                    <p className="text-xs text-muted-foreground">
                      {t('settings.usage.replayRetentionExemptionNote')}
                    </p>
                  </div>
                )}

                <div className="flex items-center justify-between gap-3">
                  <Badge variant="outline">
                    {replayRetentionEnabled
                      ? t('settings.usage.replayRetentionActive', { days: Math.max(1, Math.floor(Number(replayRetentionDays) || 1)) })
                      : t('settings.usage.replayRetentionOff')}
                  </Badge>
                  <Button
                    size="sm"
                    onClick={handleSaveReplayRetention}
                    disabled={isLoadingReplayRetention || updateReplayRetentionSettings.isPending || Boolean(replayRetentionError) || !replayRetentionSettings?.data}
                  >
                    {updateReplayRetentionSettings.isPending ? t('common.saving') : t('settings.usage.saveSettings')}
                  </Button>
                </div>
              </div>
            </CardContent>
          </Card>
        </div>
      )}

      {/* File Management Controls */}
      <Card className="rounded-card shadow-card-inner bg-surface-2">
        <CardHeader>
          <CardTitle>{t('settings.usage.fileManagement')}</CardTitle>
        </CardHeader>
        <CardContent>
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4 mb-6">
            <div>
              <Label htmlFor="search">{t('settings.usage.searchFiles')}</Label>
              <div className="relative">
                <Search className="absolute left-3 top-1/2 transform -translate-y-1/2 h-4 w-4 text-muted-foreground" />
                <Input
                  id="search"
                  placeholder={t('settings.usage.searchPlaceholder')}
                  value={searchTerm}
                  onChange={(e) => setSearchTerm(e.target.value)}
                  className="pl-10"
                />
              </div>
            </div>

            <div>
              <Label htmlFor="filter-type">{t('settings.usage.filterByType')}</Label>
              <Select value={filterType} onValueChange={setFilterType}>
                <SelectTrigger>
                  <SelectValue placeholder={t('settings.usage.allTypes')} />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="all">{t('settings.usage.allTypes')}</SelectItem>
                  <SelectItem value="ticket">{t('settings.usage.typeTickets')}</SelectItem>
                  <SelectItem value="evidence">{t('settings.usage.typeEvidence')}</SelectItem>
                  <SelectItem value="logs">{t('settings.usage.typeLogs')}</SelectItem>
                  <SelectItem value="backup">{t('settings.usage.typeBackups')}</SelectItem>
                  <SelectItem value="replay">{t('settings.usage.typeReplays')}</SelectItem>
                  <SelectItem value="other">{t('common.other')}</SelectItem>
                </SelectContent>
              </Select>
            </div>

            <div>
              <Label htmlFor="sort-by">{t('settings.usage.sortBy')}</Label>
              <Select value={sortBy} onValueChange={(value) => { if (value === 'name' || value === 'size' || value === 'date') setSortBy(value); }}>
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="name">{t('table.name')}</SelectItem>
                  <SelectItem value="size">{t('settings.usage.size')}</SelectItem>
                  <SelectItem value="date">{t('settings.usage.dateModified')}</SelectItem>
                </SelectContent>
              </Select>
            </div>

            <div>
              <Label htmlFor="sort-order">{t('settings.usage.order')}</Label>
              <Select value={sortOrder} onValueChange={(value) => { if (value === 'asc' || value === 'desc') setSortOrder(value); }}>
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="asc">{t('settings.usage.ascending')}</SelectItem>
                  <SelectItem value="desc">{t('settings.usage.descending')}</SelectItem>
                </SelectContent>
              </Select>
            </div>
          </div>

          <div className="flex justify-between items-center mb-4">
            <div className="flex items-center space-x-2">
              <Button
                variant="outline"
                size="sm"
                onClick={handleSelectAll}
                disabled={filteredAndSortedFiles.length === 0}
              >
                {selectedFiles.size === filteredAndSortedFiles.length && filteredAndSortedFiles.length > 0
                  ? t('settings.usage.deselectAll')
                  : t('settings.usage.selectAll')}
              </Button>
              {selectedFiles.size > 0 && (
                <Button
                  variant="destructive"
                  size="sm"
                  onClick={handleDeleteSelected}
                >
                  <Trash2 className="h-4 w-4 mr-2" />
                  {t('settings.usage.deleteSelected', { count: selectedFiles.size })}
                </Button>
              )}
            </div>
            <Button
              variant="outline"
              size="sm"
              onClick={fetchStorageData}
            >
              {t('common.refresh')}
            </Button>
          </div>

          {/* Files Table */}
          <div className="border rounded-lg">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead className="w-12">
                    <Checkbox
                      checked={selectedFiles.size === filteredAndSortedFiles.length && filteredAndSortedFiles.length > 0}
                      onCheckedChange={handleSelectAll}
                    />
                  </TableHead>
                  <TableHead>{t('table.name')}</TableHead>
                  <TableHead>{t('settings.usage.category')}</TableHead>
                  <TableHead>{t('settings.usage.type')}</TableHead>
                  <TableHead>{t('settings.usage.size')}</TableHead>
                  <TableHead>{t('settings.usage.modified')}</TableHead>
                  <TableHead>{t('table.actions')}</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {paginatedFiles.map((file) => (
                  <TableRow key={file.id}>
                    <TableCell>
                      <Checkbox
                        checked={selectedFiles.has(file.id)}
                        onCheckedChange={() => handleSelectFile(file.id)}
                      />
                    </TableCell>
                    <TableCell>
                      <div className="space-y-1">
                        <div className="font-medium">{file.name}</div>
                        <div className="text-sm text-muted-foreground truncate max-w-xs">
                          {file.path}
                        </div>
                      </div>
                    </TableCell>
                    <TableCell>
                      <Badge variant="outline" className={getTypeColor(file.type)}>
                        <span className="mr-1">{getTypeIcon(file.type)}</span>
                        {file.type.charAt(0).toUpperCase() + file.type.slice(1)}
                      </Badge>
                    </TableCell>
                    <TableCell>
                      <Badge variant="outline" className={getFileTypeColor(getFileType(file.name))}>
                        {getFileType(file.name)}
                      </Badge>
                    </TableCell>
                    <TableCell>{formatFileSize(file.size)}</TableCell>
                    <TableCell>
                      {new Date(file.lastModified).toLocaleDateString()}
                    </TableCell>
                    <TableCell>
                      <div className="flex items-center space-x-2">
                        <Button
                          variant="ghost"
                          size="sm"
                          onClick={() => window.open(file.url, '_blank')}
                          aria-label={t('common.download', { defaultValue: 'Download' })}
                        >
                          <Download className="h-4 w-4" />
                        </Button>
                        <Button
                          variant="ghost"
                          size="sm"
                          onClick={() => handleDeleteFile(file.id)}
                          aria-label={t('common.delete')}
                        >
                          <Trash2 className="h-4 w-4 text-destructive" />
                        </Button>
                      </div>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>

          {/* Pagination Controls */}
          {filteredAndSortedFiles.length > 0 && (
            <div className="flex items-center justify-between mt-4">
              <div className="flex items-center gap-2 text-sm text-muted-foreground">
                <span>
                  {t('settings.usage.showingFiles', { from: (safePage - 1) * itemsPerPage + 1, to: Math.min(safePage * itemsPerPage, filteredAndSortedFiles.length), total: filteredAndSortedFiles.length })}
                </span>
                <Select value={String(itemsPerPage)} onValueChange={(value) => { setItemsPerPage(Number(value)); setCurrentPage(1); }}>
                  <SelectTrigger className="w-[80px] h-8">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="10">10</SelectItem>
                    <SelectItem value="25">25</SelectItem>
                    <SelectItem value="50">50</SelectItem>
                  </SelectContent>
                </Select>
                <span>{t('settings.usage.perPage')}</span>
              </div>
              <div className="flex items-center gap-2">
                <Button
                  variant="outline"
                  size="sm"
                  onClick={() => setCurrentPage(p => Math.max(1, p - 1))}
                  disabled={safePage <= 1}
                >
                  {t('common.previous')}
                </Button>
                <span className="text-sm text-muted-foreground">
                  {t('settings.usage.pageOf', { page: safePage, total: totalPages })}
                </span>
                <Button
                  variant="outline"
                  size="sm"
                  onClick={() => setCurrentPage(p => Math.min(totalPages, p + 1))}
                  disabled={safePage >= totalPages}
                >
                  {t('common.next')}
                </Button>
              </div>
            </div>
          )}

          {filteredAndSortedFiles.length === 0 && (
            <div className="text-center py-12">
              <HardDrive className="h-12 w-12 mx-auto mb-4 text-muted-foreground" />
              <h3 className="text-lg font-medium mb-2">{t('settings.usage.noFilesFound')}</h3>
              <p className="text-muted-foreground">
                {searchTerm || filterType !== 'all'
                  ? t('settings.usage.adjustSearchFilter')
                  : t('settings.usage.noFilesUploaded')}
              </p>
            </div>
          )}
        </CardContent>
      </Card>

      {/* Delete Confirmation Dialog */}
      <AlertDialog open={showDeleteDialog} onOpenChange={setShowDeleteDialog}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>{t('common.areYouSure')}</AlertDialogTitle>
            <AlertDialogDescription>
              {deleteTarget === 'single'
                ? t('settings.usage.deleteFileConfirm')
                : t('settings.usage.deleteFilesConfirm', { count: selectedFiles.size })}
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>{t('common.cancel')}</AlertDialogCancel>
            <AlertDialogAction onClick={confirmDelete} className="bg-destructive text-destructive-foreground hover:bg-destructive/90">
              {t('common.delete')}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>

    </div>
  );
};

export default UsageSettings;
