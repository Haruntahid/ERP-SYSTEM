import { api } from '../../shared/api/client';

export type FileCategory =
  | 'SCHOOL_LOGO'
  | 'PROFILE_PHOTO'
  | 'STUDENT_DOC'
  | 'STAFF_DOC'
  | 'NOTICE_ATTACHMENT'
  | 'GENERATED_PDF'
  | 'BULK_IMPORT';

export type ScanStatus = 'CLEAN' | 'SKIPPED_DEV' | 'INFECTED';
export type FileStatus = 'TEMP' | 'ATTACHED' | 'DELETED';

export interface StoredFileDto {
  fileUuid: string;
  category: FileCategory;
  entityType?: string | null;
  entityId?: string | null;
  originalFilename: string;
  sanitizedFilename: string;
  objectKey: string;
  mimeType: string;
  sizeBytes: number;
  sha256Checksum: string;
  scanStatus: ScanStatus;
  status: FileStatus;
  uploadedByUserId?: number | null;
  createdAt: string;
}

export interface PresignedDownloadUrlDto {
  fileUuid: string;
  url: string;
  expiresAt: string;
}

export interface StorageQuotaDto {
  usedBytes: number;
  quotaBytes: number;
  fileCountByCategory: Record<string, number>;
}

export async function uploadStoredFile(params: {
  file: File;
  category: FileCategory;
  entityType?: string;
  entityId?: string;
}): Promise<StoredFileDto> {
  const formData = new FormData();
  formData.append('file', params.file);
  formData.append('category', params.category);
  if (params.entityType) formData.append('entityType', params.entityType);
  if (params.entityId) formData.append('entityId', params.entityId);

  const response = await api.post<StoredFileDto>('/api/v1/files/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
  return response.data;
}

export async function fetchStoredFileMetadata(fileUuid: string): Promise<StoredFileDto> {
  const response = await api.get<StoredFileDto>(`/api/v1/files/${encodeURIComponent(fileUuid)}`);
  return response.data;
}

export async function fetchPresignedDownloadUrl(
  fileUuid: string,
  disposition: 'inline' | 'attachment' = 'attachment',
): Promise<PresignedDownloadUrlDto> {
  const response = await api.get<PresignedDownloadUrlDto>(
    `/api/v1/files/${encodeURIComponent(fileUuid)}/download-url?disposition=${disposition}`,
  );
  return response.data;
}

export async function deleteStoredFile(fileUuid: string): Promise<void> {
  await api.delete(`/api/v1/files/${encodeURIComponent(fileUuid)}`);
}

export async function fetchStorageQuota(): Promise<StorageQuotaDto> {
  const response = await api.get<StorageQuotaDto>('/api/v1/files/quota');
  return response.data;
}

