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
      key: 'Inicio de dolor',
      category: 'Historia actual',
      title: 'Inicio de dolor',
      content: 'El dolor inició ayer.',
      triggers: ['dolor', 'inicio'],
      trigger: 'dolor, inicio',
      visibility: 'ON_QUESTION',
      revealLevel: 4
    });
  });

  it('should support cases without legacy metadata', () => {
    const response = buildBackendResponse('Indicaciones generales del paciente.');

    const detail = (service as unknown as { mapBackendCaseToDetail: (response: unknown) => Record<string, unknown> })
      .mapBackendCaseToDetail(response);

    expect(detail['context']).toBe('');
    expect(detail['behaviorGuidelines']).toBe('Indicaciones generales del paciente.');
    expect(detail['expectedDiagnosis']).toBeUndefined();
    expect(detail['legacyExpectedDiagnosis']).toBeUndefined();
  });

  it('should preserve backend clinical case status when present', () => {
    const response = {
      ...buildBackendResponse(null),
      status: 'DRAFT',
      active: true
    };

    const summary = (service as unknown as { mapBackendCaseToSummary: (response: unknown) => Record<string, unknown> })
      .mapBackendCaseToSummary(response);

    expect(summary['status']).toBe('DRAFT');
  });

  it('should fallback legacy active true to READY and active false to DRAFT', () => {
    const mapSummary = (service as unknown as { mapBackendCaseToSummary: (response: unknown) => Record<string, unknown> })
      .mapBackendCaseToSummary.bind(service);

    expect(mapSummary({ ...buildBackendResponse(null), active: true })['status']).toBe('READY');
    expect(mapSummary({ ...buildBackendResponse(null), active: false })['status']).toBe('DRAFT');
  });

  it('should send status and legacy active compatibility in upsert requests', () => {
    const payload = buildPayload({
      category: 'Historia actual',
      title: 'Inicio de dolor',
      content: 'El dolor inició ayer.',
      trigger: 'dolor',
      visibility: 'ON_QUESTION'
    });
    payload.status = 'DRAFT';

    const request = (service as unknown as { mapUpsertPayloadToBackendRequest: (payload: ClinicalCaseUpsertPayload) => Record<string, unknown> })
      .mapUpsertPayloadToBackendRequest(payload);

    expect(request['status']).toBe('DRAFT');
    expect(request['active']).toBe(false);
  });

  it('should save expectedDiagnosis metadata from form input', () => {
    const payload = buildPayload({
      category: 'Historia actual',
      title: 'Inicio de dolor',
      content: 'El dolor inició ayer.',
      trigger: 'dolor',
      visibility: 'ON_QUESTION'
    });
    payload.expectedDiagnosis = 'Diagnóstico docente nuevo';

    const request = (service as unknown as { mapUpsertPayloadToBackendRequest: (payload: ClinicalCaseUpsertPayload) => { description: string | null } })
      .mapUpsertPayloadToBackendRequest(payload);

    expect(request.description).toContain('Diagnóstico docente nuevo');
    expect(request.description).toContain('expectedDiagnosis');
  });

  it('should save edited expectedDiagnosis over legacy value on update payloads', () => {
    const payload = buildPayload({
      category: 'Historia actual',
      title: 'Inicio de dolor',
      content: 'El dolor inició ayer.',
      trigger: 'dolor',
      visibility: 'ON_QUESTION'
    });
    payload.expectedDiagnosis = 'Diagnóstico docente editado';
    payload.legacyExpectedDiagnosis = 'Diagnóstico docente legacy';

    const request = (service as unknown as { mapUpsertPayloadToBackendRequest: (payload: ClinicalCaseUpsertPayload) => { description: string | null } })
      .mapUpsertPayloadToBackendRequest(payload);

    expect(request.description).toContain('expectedDiagnosis');
    expect(request.description).toContain('Diagnóstico docente editado');
    expect(request.description).not.toContain('Diagnóstico docente legacy');
  });
});

function buildBackendResponse(description: string | null): Record<string, unknown> {
  return {
    id: 'case-1',
    title: 'Caso',
    description,
    patientName: 'Paciente',
    patientAge: 40,
    patientSex: 'F',
    chiefComplaint: 'Dolor',
    noInformationPhrase: 'No sé',
    active: true,
    createdAt: '2026-06-06T00:00:00',
    facts: [],
    personality: []
  };
}

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
