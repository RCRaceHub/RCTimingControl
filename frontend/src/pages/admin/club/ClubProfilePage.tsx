import { useRef } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { Loader2, Upload } from 'lucide-react';
import { toast } from 'sonner';

import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { useClubProfile, useUpdateClubProfile, useUploadClubLogo } from '@/hooks/admin/useAdminClub';
import type { UpdateClubProfileRequest } from '@/lib/adminApi';
import axios from 'axios';

const schema = z.object({
  name: z.string().min(1, 'Club name is required'),
  email: z.string().email().nullable().or(z.literal('')),
  phone: z.string().max(50).nullable().or(z.literal('')),
  websiteUrl: z.string().url().nullable().or(z.literal('')),
  latitude: z.coerce.number().nullable(),
  longitude: z.coerce.number().nullable(),
  timezone: z.string().min(1, 'Timezone is required'),
  logoType: z.string().nullable().or(z.literal('')),
});

type FormValues = z.infer<typeof schema>;

function toRequest(values: FormValues): UpdateClubProfileRequest {
  return {
    name: values.name,
    email: values.email || null,
    phone: values.phone || null,
    websiteUrl: values.websiteUrl || null,
    latitude: values.latitude,
    longitude: values.longitude,
    timezone: values.timezone,
    logoType: values.logoType || null,
  };
}

export default function ClubProfilePage() {
  const { data, isLoading, isError, refetch } = useClubProfile();
  const updateMutation = useUpdateClubProfile();
  const uploadLogoMutation = useUploadClubLogo();
  const fileInputRef = useRef<HTMLInputElement>(null);

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting, isDirty },
    reset,
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    values: data
      ? {
          name: data.name ?? '',
          email: data.email ?? '',
          phone: data.phone ?? '',
          websiteUrl: data.websiteUrl ?? '',
          latitude: data.latitude ?? null,
          longitude: data.longitude ?? null,
          timezone: data.timezone ?? '',
          logoType: data.logoType ?? '',
        }
      : undefined,
  });

  async function onSave(values: FormValues) {
    try {
      await updateMutation.mutateAsync(toRequest(values));
      toast.success('Club profile saved');
      reset(values);
    } catch {
      toast.error('Could not save club profile. Try again.');
    }
  }

  async function handleFileChange(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0];
    if (!file) return;
    try {
      await uploadLogoMutation.mutateAsync(file);
      toast.success('Logo uploaded');
    } catch (err) {
      if (axios.isAxiosError(err) && err.response?.status === 400) {
        toast.error(err.response.data?.message ?? 'Upload rejected — check file type and size (max 2 MB).');
      } else {
        toast.error('Logo upload failed. Try again.');
      }
    }
    if (fileInputRef.current) fileInputRef.current.value = '';
  }

  if (isLoading) {
    return (
      <div className="flex items-center justify-center py-20">
        <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
      </div>
    );
  }

  if (isError) {
    return (
      <div className="flex flex-col items-center justify-center py-20 gap-4 text-center">
        <p className="text-muted-foreground">Failed to load club profile.</p>
        <Button variant="outline" onClick={() => void refetch()}>Retry</Button>
      </div>
    );
  }

  return (
    <div className="max-w-xl space-y-8">
      <h1 className="text-2xl font-semibold">Club Profile</h1>

      <div className="rounded-lg border p-4 space-y-3">
        <h2 className="text-sm font-semibold">Club Logo</h2>
        {data?.logoUrl ? (
          <img
            src={data.logoUrl}
            alt="Club logo"
            className="h-32 w-auto object-contain border rounded"
          />
        ) : (
          <div className="h-32 w-32 rounded border flex items-center justify-center bg-muted">
            <span className="text-xs text-muted-foreground">No logo uploaded</span>
          </div>
        )}
        <div>
          <input
            ref={fileInputRef}
            type="file"
            className="hidden"
            accept="image/png,image/jpeg,image/webp,image/svg+xml"
            onChange={handleFileChange}
          />
          <Button
            variant="outline"
            size="sm"
            disabled={uploadLogoMutation.isPending}
            onClick={() => fileInputRef.current?.click()}
          >
            {uploadLogoMutation.isPending ? (
              <Loader2 className="h-4 w-4 animate-spin mr-1" />
            ) : (
              <Upload className="h-4 w-4 mr-1" />
            )}
            Upload Logo
          </Button>
          <p className="text-xs text-muted-foreground mt-1">PNG, JPEG, WebP, or SVG. Max 2 MB.</p>
        </div>
      </div>

      <form onSubmit={handleSubmit(onSave)} className="space-y-4">
        <div className="space-y-1.5">
          <Label htmlFor="club-name">Club Name</Label>
          <Input id="club-name" {...register('name')} />
          {errors.name && <p className="text-xs text-destructive">{errors.name.message}</p>}
        </div>
        <div className="grid grid-cols-2 gap-4">
          <div className="space-y-1.5">
            <Label htmlFor="club-email">Email</Label>
            <Input id="club-email" type="email" {...register('email')} />
            {errors.email && <p className="text-xs text-destructive">{errors.email.message}</p>}
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="club-phone">Phone</Label>
            <Input id="club-phone" {...register('phone')} />
          </div>
        </div>
        <div className="space-y-1.5">
          <Label htmlFor="club-website">Website URL</Label>
          <Input id="club-website" type="url" {...register('websiteUrl')} placeholder="https://" />
          {errors.websiteUrl && <p className="text-xs text-destructive">{errors.websiteUrl.message}</p>}
        </div>
        <div className="grid grid-cols-2 gap-4">
          <div className="space-y-1.5">
            <Label htmlFor="club-lat">Latitude</Label>
            <Input id="club-lat" type="number" step="any" {...register('latitude')} />
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="club-lng">Longitude</Label>
            <Input id="club-lng" type="number" step="any" {...register('longitude')} />
          </div>
        </div>
        <div className="space-y-1.5">
          <Label htmlFor="club-tz">Timezone</Label>
          <Input id="club-tz" {...register('timezone')} placeholder="e.g. Europe/London" />
          {errors.timezone && <p className="text-xs text-destructive">{errors.timezone.message}</p>}
        </div>
        <Button type="submit" disabled={!isDirty || isSubmitting || updateMutation.isPending}>
          {updateMutation.isPending ? 'Saving…' : 'Save Profile'}
        </Button>
      </form>
    </div>
  );
}
