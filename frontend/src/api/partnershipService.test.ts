import { describe, expect, it } from 'vitest';
import { refusConvention } from './partnershipService';

/** Un fichier d'une taille donnée, sans allouer cette taille : seul `size` est lu. */
function fichier(nom: string, taille: number): File {
  const f = new File(['x'], nom);
  Object.defineProperty(f, 'size', { value: taille });
  return f;
}

describe('refusConvention', () => {
  it('laisse passer les trois formats que le serveur accepte, quelle que soit la casse', () => {
    for (const nom of ['convention.pdf', 'Convention signée.DOCX', 'convention.doc']) {
      expect(refusConvention(fichier(nom, 200_000)), nom).toBeNull();
    }
  });

  it('refuse les autres formats, y compris le classeur du modèle et un fichier sans extension', () => {
    for (const nom of ['convention.xlsx', 'convention.zip', 'convention.pdf.exe', 'convention']) {
      expect(refusConvention(fichier(nom, 200_000)), nom).toMatch(/PDF, un DOC ou un DOCX/);
    }
  });

  it('refuse au-delà de 10 Mo, et accepte exactement 10 Mo', () => {
    expect(refusConvention(fichier('convention.pdf', 10 * 1024 * 1024))).toBeNull();
    expect(refusConvention(fichier('convention.pdf', 10 * 1024 * 1024 + 1))).toMatch(/10 Mo/);
  });
});
