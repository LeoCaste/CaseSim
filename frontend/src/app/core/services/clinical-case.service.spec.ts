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

  it('should map estimatedTimeMinutes from backend response', () => {
    const response = {
      ...buildBackendResponse(null),
      estimatedTimeMinutes: 45
    };

    const summary = (service as unknown as { mapBackendCaseToSummary: (response: unknown) => Record<string, unknown> })
      .mapBackendCaseToSummary(response);

    expect(summary['estimatedTimeMinutes']).toBe(45);
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

  it('should fallback legacy active true to READY and active false to ARCHIVED', () => {
    const mapSummary = (service as unknown as { mapBackendCaseToSummary: (response: unknown) => Record<string, unknown> })
      .mapBackendCaseToSummary.bind(service);

    expect(mapSummary({ ...buildBackendResponse(null), active: true })['status']).toBe('READY');
    expect(mapSummary({ ...buildBackendResponse(null), active: false })['status']).toBe('ARCHIVED');
  });

  it('should send status and legacy active compatibility in upsert requests', () => {
    const payload = buildPayload({
      category: 'Historia actual',
      title: 'Inicio de dolor',
      content: 'El dolor inició ayer.',
      trigger: 'dolor',
      visibility: 'ON_QUESTION'
    });
    payload.status = 'ARCHIVED';

    const request = (service as unknown as { mapUpsertPayloadToBackendRequest: (payload: ClinicalCaseUpsertPayload) => Record<string, unknown> })
      .mapUpsertPayloadToBackendRequest(payload);

    expect(request['status']).toBe('ARCHIVED');
    expect(request['active']).toBe(false);
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

  it('should load legacy description metadata without losing expectedDiagnosis', () => {
    const response = buildBackendResponse(
      '[CASESIM_META]' +
        JSON.stringify({
          context: 'Box de urgencia',
          initialMessage: 'Me duele el pecho',
          expectedDiagnosis: 'Síndrome coronario a descartar',
          behaviorGuidelines: 'Responder breve'
        })
    );

    const detail = (service as unknown as { mapBackendCaseToDetail: (response: unknown) => Record<string, unknown> })
      .mapBackendCaseToDetail(response);

    expect(detail['context']).toBe('Box de urgencia');
    expect(detail['initialMessage']).toBe('Me duele el pecho');
    expect(detail['expectedDiagnosis']).toBe('Síndrome coronario a descartar');
    expect(detail['legacyExpectedDiagnosis']).toBe('Síndrome coronario a descartar');
    expect(detail['behaviorGuidelines']).toBe('Responder breve');
    expect((detail['personality'] as Record<string, unknown>)['behaviorNotes']).not.toContain('[CASESIM_META]');
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

  it('should not create new expectedDiagnosis metadata from form input', () => {
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

    expect(request.description).not.toContain('Diagnóstico docente nuevo');
    expect(request.description).not.toContain('expectedDiagnosis');
  });

  it('should preserve existing legacy expectedDiagnosis metadata on update payloads', () => {
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
    expect(request.description).toContain('Diagnóstico docente legacy');
    expect(request.description).not.toContain('Diagnóstico docente editado');
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
