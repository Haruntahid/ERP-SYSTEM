import { useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useI18n } from '../../shared/i18n';
import {
  evictSchoolCache,
  revokeAllUserSessions,
  unlockUserAccount,
  updateSetting,
} from './api';
import { useSettings } from './hooks';

interface SecurityAndCacheSettingsPanelProps {
  selectedUserId?: number;
  isUserLocked?: boolean;
}

export function SecurityAndCacheSettingsPanel({
  selectedUserId,
  isUserLocked = false,
}: SecurityAndCacheSettingsPanelProps) {
  const { t } = useI18n();
  const { settings } = useSettings();
  const queryClient = useQueryClient();

  const [maxAttempts, setMaxAttempts] = useState<string>(
    settings['security.lockout.max_attempts'] ?? '5',
  );
  const [lockoutDuration, setLockoutDuration] = useState<string>(
    settings['security.lockout.duration_minutes'] ?? '15',
  );
  const [feedback, setFeedback] = useState<string | null>(null);

  const saveSettingsMutation = useMutation({
    mutationFn: async () => {
      await updateSetting('security.lockout.max_attempts', maxAttempts);
      await updateSetting('security.lockout.duration_minutes', lockoutDuration);
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['settings'] });
    },
  });

  const evictCacheMutation = useMutation({
    mutationFn: () => evictSchoolCache(['all']),
    onSuccess: () => {
      setFeedback(t('settings.cache.evict_success'));
      void queryClient.invalidateQueries({ queryKey: ['settings'] });
    },
  });

  const unlockMutation = useMutation({
    mutationFn: (userId: number) => unlockUserAccount(userId),
    onSuccess: () => {
      setFeedback(t('security.lockout.unlock_success'));
    },
  });

  const revokeSessionsMutation = useMutation({
    mutationFn: (userId: number) => revokeAllUserSessions(userId),
    onSuccess: () => {
      setFeedback(t('security.sessions.revoke_success'));
    },
  });

  return (
    <section aria-label="Security and Cache Controls">
      <div>
        <label htmlFor="security-max-attempts">{t('settings.security.max_attempts')}</label>
        <input
          id="security-max-attempts"
          type="number"
          min={1}
          max={20}
          value={maxAttempts}
          onChange={(e) => setMaxAttempts(e.target.value)}
        />
      </div>

      <div>
        <label htmlFor="security-lockout-duration">{t('settings.security.lockout_duration')}</label>
        <input
          id="security-lockout-duration"
          type="number"
          min={1}
          max={1440}
          value={lockoutDuration}
          onChange={(e) => setLockoutDuration(e.target.value)}
        />
      </div>

      <div>
        <button
          type="button"
          onClick={() => saveSettingsMutation.mutate()}
          disabled={saveSettingsMutation.isPending}
        >
          {t('common.save')}
        </button>
        <button
          type="button"
          onClick={() => evictCacheMutation.mutate()}
          disabled={evictCacheMutation.isPending}
        >
          {t('settings.cache.evict_btn')}
        </button>
      </div>

      {selectedUserId !== undefined && (
        <div>
          {isUserLocked && <span>{t('security.lockout.badge')}</span>}
          <button
            type="button"
            onClick={() => unlockMutation.mutate(selectedUserId)}
            disabled={unlockMutation.isPending}
          >
            {t('security.lockout.unlock_btn')}
          </button>
          <button
            type="button"
            onClick={() => revokeSessionsMutation.mutate(selectedUserId)}
            disabled={revokeSessionsMutation.isPending}
          >
            {t('security.sessions.revoke_all_btn')}
          </button>
        </div>
      )}

      {feedback && <p role="status">{feedback}</p>}
    </section>
  );
}

