import { ClinicalCaseService } from './clinical-case.service';
import { ClinicalCaseUpsertPayload } from '../models/clinical-case.model';

describe('ClinicalCaseService fact mapping', () => {
  let service: ClinicalCaseService;

  beforeEach(() => {
    service = new ClinicalCaseService({} as never);
  });

  it('should send complete facts with category, triggers, content and revealLevel', () => {
    const payload = buildPayload({
      category: 'Historia actual',
      title: 'Inicio de dolor',
      content: 'El dolor inició ayer.',
      trigger: 'dolor, inicio, evolución',
      visibility: 'ON_QUESTION',
      revealLevel: 3
    });

    const request = (service as unknown as { mapUpsertPayloadToBackendRequest: (payload: ClinicalCaseUpsertPayload) => unknown })
      .mapUpsertPayloadToBackendRequest(payload) as { facts: Array<Record<string, unknown>> };

    expect(request.facts).toEqual([
      {
        category: 'Historia actual',
        key: 'Inicio de dolor',
        content: 'El dolor inició ayer.',
        triggers: ['dolor', 'inicio', 'evolución'],
        revealLevel: 3
      }
    ]);
  });

  it('should recover complete facts from backend response without degrading revealLevel', () => {
    const response = {
      id: 'case-1',
      title: 'Caso',
      description: null,
      patientName: 'Paciente',
      patientAge: 40,
      patientSex: 'F',
      chiefComplaint: 'Dolor',
      noInformationPhrase: 'No sé',
      active: true,
      createdAt: '2026-06-06T00:00:00',
      facts: [
        {
          category: 'Historia actual',
          key: 'Inicio de dolor',
          content: 'El dolor inició ayer.',
          triggers: ['dolor', 'inicio'],
          revealLevel: 4
        }
      ],
      personality: []
    };

    const detail = (service as unknown as { mapBackendCaseToDetail: (response: unknown) => { facts: Array<Record<string, unknown>> } })
      .mapBackendCaseToDetail(response);

    expect(detail.facts[0]).toEqual({
      category: 'Historia actual',
      title: 'Inicio de dolor',
      content: 'El dolor inició ayer.',
      trigger: 'dolor, inicio',
      visibility: 'ON_QUESTION',
      revealLevel: 4
    });
  });

  it('should not send completely empty fact content', () => {
    const payload = buildPayload({
      category: 'Historia actual',
      title: 'Vacío',
      content: '   ',
      trigger: 'dolor',
      visibility: 'ON_QUESTION'
    });

    const request = (service as unknown as { mapUpsertPayloadToBackendRequest: (payload: ClinicalCaseUpsertPayload) => unknown })
      .mapUpsertPayloadToBackendRequest(payload) as { facts: Array<Record<string, unknown>> };

    expect(request.facts).toEqual([]);
  });
});

function buildPayload(fact: ClinicalCaseUpsertPayload['facts'][number]): ClinicalCaseUpsertPayload {
  return {
    title: 'Caso',
    patientName: 'Paciente',
    status: 'READY',
    age: 40,
    sex: 'F',
    context: 'Consulta',
    reason: 'Dolor',
    initialMessage: 'Me duele',
    fallbackResponse: 'No sé',
    personality: {
      tone: 'Natural',
      detailLevel: 'Breve',
      behaviorNotes: 'Responder solo lo preguntado'
    },
    facts: [fact]
  };
}
