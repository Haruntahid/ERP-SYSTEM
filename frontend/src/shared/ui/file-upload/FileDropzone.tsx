import { type ChangeEvent, type DragEvent, useRef, useState } from 'react';
import {
  type FileCategory,
  type StoredFileDto,
  uploadStoredFile,
} from '../../../features/files/api';
import { useI18n } from '../../i18n';

interface FileDropzoneProps {
  category: FileCategory;
  maxSizeMb?: number;
  allowedMimeTypes?: string[];
  entityType?: string;
  entityId?: string;
  onUploaded: (file: StoredFileDto) => void;
}

export function FileDropzone({
  category,
  maxSizeMb = 10,
  allowedMimeTypes = ['image/jpeg', 'image/png', 'image/webp', 'application/pdf'],
  entityType,
  entityId,
  onUploaded,
}: FileDropzoneProps) {
  const { t } = useI18n();
  const inputRef = useRef<HTMLInputElement | null>(null);
  const [uploading, setUploading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleSelectedFile = async (file: File) => {
    setError(null);
    const maxBytes = maxSizeMb * 1024 * 1024;
    if (file.size > maxBytes) {
      setError(t('files.upload.error.too_large').replace('{{maxMb}}', String(maxSizeMb)));
      return;
    }
    if (allowedMimeTypes.length > 0 && file.type && !allowedMimeTypes.includes(file.type)) {
      setError(t('files.upload.error.invalid_type'));
      return;
    }

    setUploading(true);
    try {
      const uploaded = await uploadStoredFile({
        file,
        category,
        entityType,
        entityId,
      });
      onUploaded(uploaded);
    } catch {
      setError(t('files.upload.error.invalid_type'));
    } finally {
      setUploading(false);
    }
  };

  const handleInputChange = (e: ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (file) {
      void handleSelectedFile(file);
    }
  };

  const handleDrop = (e: DragEvent<HTMLDivElement>) => {
    e.preventDefault();
    const file = e.dataTransfer.files?.[0];
    if (file) {
      void handleSelectedFile(file);
    }
  };

  return (
    <div>
      <div
        role="button"
        tabIndex={0}
        onDragOver={(e) => e.preventDefault()}
        onDrop={handleDrop}
        onClick={() => inputRef.current?.click()}
        onKeyDown={(e) => {
          if (e.key === 'Enter' || e.key === ' ') {
            inputRef.current?.click();
          }
        }}
        style={{
          border: '2px dashed #9ca3af',
          borderRadius: 8,
          padding: 20,
          textAlign: 'center',
          cursor: 'pointer',
          background: '#f9fafb',
        }}
      >
        <input
          ref={inputRef}
          type="file"
          accept={allowedMimeTypes.join(',')}
          style={{ display: 'none' }}
          onChange={handleInputChange}
        />
        <p style={{ margin: 0, fontWeight: 500 }}>
          {uploading ? t('common.loading') : t('files.upload.dropzone_prompt')}
        </p>
        <small style={{ color: '#6b7280' }}>
          {t('files.upload.max_size_hint')
            .replace('{{formats}}', allowedMimeTypes.join(', '))
            .replace('{{maxMb}}', String(maxSizeMb))}
        </small>
      </div>
      {error && (
        <p role="alert" style={{ color: '#b91c1c', fontSize: 13, marginTop: 6 }}>
          {error}
        </p>
      )}
    </div>
  );
}

