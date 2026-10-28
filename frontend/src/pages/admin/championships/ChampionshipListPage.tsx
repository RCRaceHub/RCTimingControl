import { useRoles } from '@/hooks/useRoles';
import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Plus } from 'lucide-react';
import { toast } from 'sonner';

import { Button } from '@/components/ui/button';
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table';
import { ChampionshipConfigForm } from './ChampionshipConfigForm';
import {
  useChampionshipsList,
  useCreateChampionship,
} from '@/hooks/admin/useAdminChampionships';
import type { ChampionshipDto } from '@/lib/adminApi';
import { QueryError } from '@/components/QueryError';

export default function ChampionshipListPage() {
  const navigate = useNavigate();
  const [createOpen, setCreateOpen] = useState(false);
  const { isAdmin } = useRoles();

  const { data: championships, isLoading, isError, refetch } = useChampionshipsList();
  const createChampionship = useCreateChampionship();

  async function handleCreate(body: Omit<ChampionshipDto, 'id'>) {
    await createChampionship.mutateAsync(body);
    toast.success('Championship created');
    setCreateOpen(false);
  }

  return (
    <div>
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-2xl font-semibold">Championships</h1>
        {isAdmin && (
          <Button onClick={() => setCreateOpen(true)}>
            <Plus className="h-4 w-4 mr-1" />
            Create Championship
          </Button>
        )}
      </div>

      {isError && (
        <QueryError variant="banner" message="Failed to load championships." onRetry={refetch} />
      )}

      {isLoading ? (
        <div className="space-y-2">
          {[...Array(3)].map((_, i) => (
            <div key={i} className="h-12 rounded-lg bg-muted animate-pulse" />
          ))}
        </div>
      ) : championships && championships.length === 0 ? (
        <div className="flex flex-col items-center justify-center py-20 text-center">
          <h2 className="text-xl font-semibold mb-2">No championships yet</h2>
          <p className="text-muted-foreground text-sm mb-6">
            {isAdmin ? 'Create your first championship series to get started.' : 'An admin has not created any championships yet.'}
          </p>
          {isAdmin && (
            <Button onClick={() => setCreateOpen(true)}>
              <Plus className="h-4 w-4 mr-1" />
              Create Championship
            </Button>
          )}
        </div>
      ) : (
        <div className="rounded-lg border">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Name</TableHead>
                <TableHead>Best X/Y</TableHead>
                <TableHead className="w-24" />
              </TableRow>
            </TableHeader>
            <TableBody>
              {(championships ?? []).map(c => (
                <TableRow key={c.id}>
                  <TableCell className="font-medium">{c.name}</TableCell>
                  <TableCell>
                    {c.bestXFromYX != null && c.bestXFromYY != null
                      ? `${c.bestXFromYX}/${c.bestXFromYY}`
                      : <span className="text-muted-foreground">—</span>}
                  </TableCell>
                  <TableCell>
                    <Button
                      variant="outline"
                      size="sm"
                      onClick={() => navigate(`/admin/championships/${c.id}`)}
                    >
                      View
                    </Button>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </div>
      )}

      <Dialog open={createOpen} onOpenChange={setCreateOpen}>
        <DialogContent className="max-w-lg">
          <DialogHeader>
            <DialogTitle>Create Championship</DialogTitle>
          </DialogHeader>
          <ChampionshipConfigForm
            onSubmit={handleCreate}
            submitLabel="Create Championship"
          />
        </DialogContent>
      </Dialog>
    </div>
  );
}
