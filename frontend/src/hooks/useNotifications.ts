import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { notificationService } from '../api/notificationService';

const hasToken = () => !!localStorage.getItem('token');

/**
 * Pastille « non lues » — interrogation périodique (30 s). C'est le MVP temps réel : simple,
 * sans dépendance serveur supplémentaire. Le passage en SSE est prévu ultérieurement.
 */
export function useUnreadCount() {
  return useQuery({
    queryKey: ['notifications', 'unread-count'],
    queryFn: notificationService.unreadCount,
    refetchInterval: 30_000,
    refetchOnWindowFocus: true,
    enabled: hasToken(),
  });
}

/** Liste des dernières notifications — chargée seulement quand le panneau est ouvert. */
export function useNotificationList(enabled: boolean) {
  return useQuery({
    queryKey: ['notifications', 'list'],
    queryFn: () => notificationService.list(0, 15, false),
    enabled: enabled && hasToken(),
  });
}

export function useNotificationActions() {
  const queryClient = useQueryClient();
  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['notifications'] });

  const markRead = useMutation({ mutationFn: notificationService.markRead, onSuccess: invalidate });
  const markAllRead = useMutation({ mutationFn: notificationService.markAllRead, onSuccess: invalidate });
  const acknowledge = useMutation({ mutationFn: notificationService.acknowledge, onSuccess: invalidate });
  const snooze = useMutation({
    mutationFn: ({ id, hours }: { id: string; hours?: number }) => notificationService.snooze(id, hours),
    onSuccess: invalidate,
  });

  return { markRead, markAllRead, acknowledge, snooze };
}
