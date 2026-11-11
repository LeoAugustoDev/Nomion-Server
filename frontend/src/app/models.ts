export interface Page<T> {
  content: T[];
  page: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
}

export interface PublicEntity {
  id: string;
  name: string;
  cnpj: string | null;
  type: string;
  municipality: string;
  state: string;
  email: string | null;
  phone: string | null;
  active: boolean;
}

export interface Contract {
  id: string;
  number: string;
  object: string;
  publicEntity: { id: string; name: string };
  manager: { name: string } | null;
  amount: number | null;
  endDate: string;
  status: string;
}

export const entityTypes: Record<string, string> = {
  CITY_HALL: 'Prefeitura',
  CITY_COUNCIL: 'Câmara Municipal',
  AUTARCHY: 'Autarquia',
  FOUNDATION: 'Fundação',
  PUBLIC_CONSORTIUM: 'Consórcio Público',
  OTHER: 'Outro',
};

export const contractStatuses: Record<string, string> = {
  DRAFT: 'Rascunho',
  ACTIVE: 'Ativo',
  SUSPENDED: 'Suspenso',
  EXPIRED: 'Expirado',
  TERMINATED: 'Encerrado',
};

export function errorMessage(error: unknown): string {
  if (error && typeof error === 'object' && 'error' in error) {
    const detail = error.error;
    if (
      detail &&
      typeof detail === 'object' &&
      'message' in detail &&
      typeof detail.message === 'string'
    ) {
      return detail.message;
    }
  }
  return 'Não foi possível concluir a operação. Tente novamente.';
}
