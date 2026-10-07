import React, { ReactNode } from 'react';
import { EmptyState } from './EmptyState';
import { getApiErrorPresentation } from '../../services/api';

interface QueryStateProps {
  isLoading: boolean;
  isError: boolean;
  error?: unknown;
  isEmpty: boolean;
  loading: ReactNode;
  children: ReactNode;
  emptyTitle: string;
  emptyDescription: string;
  emptyActionText?: string;
  onEmptyAction?: () => void;
  onRetry: () => void;
}

export const QueryState: React.FC<QueryStateProps> = ({
  isLoading,
  isError,
  error,
  isEmpty,
  loading,
  children,
  emptyTitle,
  emptyDescription,
  emptyActionText,
  onEmptyAction,
  onRetry,
}) => {
  if (isLoading) return <>{loading}</>;

  if (isError) {
    const presentation = getApiErrorPresentation(error);
    return (
      <EmptyState
        title={presentation.title}
        description={presentation.description}
        actionText={presentation.retryable ? 'Thử lại' : undefined}
        onAction={presentation.retryable ? onRetry : undefined}
      />
    );
  }

  if (isEmpty) {
    return (
      <EmptyState
        title={emptyTitle}
        description={emptyDescription}
        actionText={emptyActionText}
        onAction={onEmptyAction}
      />
    );
  }

  return <>{children}</>;
};
