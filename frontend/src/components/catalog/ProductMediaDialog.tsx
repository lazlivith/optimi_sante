import { useCallback, useEffect, useRef, useState } from 'react';
import {
  Loader2, X, Upload, Trash2, Video, Link2, Star, StarOff, Images, ImageOff, Check,
} from 'lucide-react';
import {
  productMediaService, visuelAFaire, HEBERGEURS_VIDEO,
  TAILLE_MAX_IMAGE_MO, TAILLE_MAX_VIDEO_MO, VISUELS_MAX,
  type ProductMedia, type VideoProvider,
} from '../../api/productMediaService';
import { Toast, type ToastType } from '../common/Toast';

/**
 * Édition des médias d'un produit : vignette, galerie, vidéo.
 *
 * <p>Conçue pour le traitement en série des fiches incomplètes : la vignette est la première
 * chose qu'on voit, un glisser-déposer suffit, et le résultat s'affiche immédiatement. La
 * galerie et la vidéo viennent ensuite, pour les fiches qu'on veut soigner.</p>
 */
export function ProductMediaDialog({
  productId, productName, onClose, onChanged,
}: {
  productId: string;
  productName: string;
  onClose: () => void;
  /** Prévient la page appelante : la vignette a changé, le compteur doit se recalculer. */
  onChanged: () => void;
}) {
  const [media, setMedia] = useState<ProductMedia | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [busy, setBusy] = useState<string | null>(null);
  const [toast, setToast] = useState<{ message: string; type: ToastType } | null>(null);

  const [lienVideo, setLienVideo] = useState('');
  const [hebergeur, setHebergeur] = useState<VideoProvider>('YOUTUBE');

  const champVignette = useRef<HTMLInputElement>(null);
  const champGalerie = useRef<HTMLInputElement>(null);
  const champVideo = useRef<HTMLInputElement>(null);

  const charger = useCallback(async () => {
    try {
      setMedia(await productMediaService.get(productId));
    } catch {
      setToast({ message: 'Impossible de charger les médias de ce produit.', type: 'error' });
    } finally {
      setIsLoading(false);
    }
  }, [productId]);

  useEffect(() => { charger(); }, [charger]);

  /** Le serveur a le dernier mot sur les tailles et les formats : son message est plus précis
   *  que tout ce qu'on pourrait deviner ici, et il nomme le fichier reçu. */
  const echec = (err: any, defaut: string) =>
    setToast({ message: err?.response?.data?.message ?? defaut, type: 'error' });

  const agir = async (cle: string, action: () => Promise<ProductMedia>, succes: string) => {
    setBusy(cle);
    try {
      setMedia(await action());
      setToast({ message: succes, type: 'success' });
      onChanged();
    } catch (err) {
      echec(err, "L'opération a échoué.");
    } finally {
      setBusy(null);
    }
  };

  const deposerVignette = (f: File | undefined) => {
    if (!f) return;
    agir('vignette', () => productMediaService.replaceMainImage(productId, f),
      'Visuel principal mis à jour.');
  };

  const deposerVideo = (f: File | undefined) => {
    if (!f) return;
    agir('video', () => productMediaService.uploadVideo(productId, f), 'Vidéo téléversée.');
  };

  const poserLien = () => {
    if (!lienVideo.trim()) {
      setToast({ message: "Indiquez l'adresse de la vidéo.", type: 'error' });
      return;
    }
    agir('lien', () => productMediaService.setVideoLink(productId, lienVideo.trim(), hebergeur),
      'Vidéo rattachée.').then(() => setLienVideo(''));
  };

  const ajouterGalerie = async (fichiers: FileList | null) => {
    if (!fichiers || fichiers.length === 0) return;
    setBusy('galerie');
    try {
      await productMediaService.addToGallery(productId, Array.from(fichiers));
      setMedia(await productMediaService.get(productId));
      setToast({ message: 'Visuels ajoutés à la galerie.', type: 'success' });
    } catch (err) {
      echec(err, "L'ajout a échoué.");
    } finally {
      setBusy(null);
    }
  };

  const retirerVisuel = async (imageId: string) => {
    setBusy(imageId);
    try {
      await productMediaService.removeFromGallery(imageId);
      setMedia(await productMediaService.get(productId));
    } catch (err) {
      echec(err, 'Le retrait a échoué.');
    } finally {
      setBusy(null);
    }
  };

  const aFaire = media ? visuelAFaire(media.imageUrl) : false;
  const exemple = HEBERGEURS_VIDEO.find((h) => h.value === hebergeur)?.exemple ?? '';
  const placesGalerie = media ? VISUELS_MAX - media.gallery.length : 0;

  return (
    <div className="fixed inset-0 z-[60] flex items-center justify-center p-4 bg-slate-900/50 backdrop-blur-sm">
      <div className="bg-white rounded-2xl w-full max-w-3xl max-h-[92vh] overflow-y-auto shadow-xl border border-slate-200">

        <div className="sticky top-0 z-10 flex items-start justify-between gap-4 border-b border-slate-100 bg-slate-50 px-6 py-5">
          <div className="min-w-0">
            <h2 className="text-lg font-bold text-brand-dark">Médias du produit</h2>
            <p className="truncate text-sm text-slate-500">{productName}</p>
          </div>
          <button type="button" onClick={onClose}
            className="rounded-lg p-1 transition-colors hover:bg-slate-200">
            <X className="h-5 w-5 text-slate-500" />
          </button>
        </div>

        {isLoading || !media ? (
          <div className="flex justify-center p-16"><Loader2 className="h-6 w-6 animate-spin text-brand" /></div>
        ) : (
          <div className="space-y-8 px-6 py-6">

            {/* ------------------------------------------------------- vignette -- */}
            <section>
              <div className="mb-3 flex items-center justify-between gap-3">
                <h3 className="text-sm font-bold text-brand-dark">Visuel principal</h3>
                {aFaire && (
                  <span className="inline-flex items-center gap-1.5 rounded-full bg-amber-100 px-2.5 py-1 text-xs font-semibold text-amber-800">
                    <ImageOff className="h-3.5 w-3.5" /> à faire
                  </span>
                )}
              </div>

              <div className="flex flex-wrap items-center gap-5">
                <div className={`h-32 w-32 shrink-0 overflow-hidden rounded-xl border ${
                  aFaire ? 'border-amber-300 bg-amber-50' : 'border-slate-200 bg-slate-50'}`}>
                  {media.imageUrl ? (
                    <img src={media.imageUrl} alt="" className="h-full w-full object-contain" />
                  ) : (
                    <div className="flex h-full items-center justify-center">
                      <ImageOff className="h-7 w-7 text-slate-300" />
                    </div>
                  )}
                </div>

                <div className="flex-1 min-w-[15rem]">
                  <button type="button" disabled={busy !== null}
                    onClick={() => champVignette.current?.click()}
                    className="inline-flex items-center gap-2 rounded-xl bg-brand px-5 py-2.5 text-sm font-bold text-white transition-colors hover:bg-[#0f3c35] disabled:opacity-50">
                    {busy === 'vignette'
                      ? <Loader2 className="h-4 w-4 animate-spin" />
                      : <Upload className="h-4 w-4" />}
                    {aFaire ? 'Déposer la vraie photo' : 'Remplacer le visuel'}
                  </button>
                  <p className="mt-2 text-xs text-slate-500">
                    JPEG, PNG, WebP ou AVIF — {TAILLE_MAX_IMAGE_MO} Mo maximum.
                  </p>
                  <input ref={champVignette} type="file" hidden accept="image/*"
                    onChange={(e) => { deposerVignette(e.target.files?.[0]); e.target.value = ''; }} />
                </div>
              </div>
            </section>

            {/* -------------------------------------------------------- galerie -- */}
            <section className="border-t border-slate-100 pt-7">
              <div className="mb-3 flex items-center justify-between gap-3">
                <h3 className="inline-flex items-center gap-2 text-sm font-bold text-brand-dark">
                  <Images className="h-4 w-4 text-slate-400" />
                  Galerie de détails
                </h3>
                <span className="text-xs text-slate-500">
                  {media.gallery.length} / {VISUELS_MAX}
                </span>
              </div>
              <p className="mb-3 text-sm text-slate-600">
                Angles complémentaires, zooms, schémas techniques. Ces visuels s'affichent en
                carrousel sur la fiche produit — jamais dans la liste du catalogue.
              </p>

              {media.gallery.length > 0 && (
                <ul className="mb-4 grid grid-cols-3 gap-3 sm:grid-cols-5">
                  {media.gallery.map((v) => (
                    <li key={v.id} className="group relative aspect-square overflow-hidden rounded-lg border border-slate-200 bg-slate-50">
                      <img src={v.imageUrl} alt={v.caption ?? ''} className="h-full w-full object-contain" />
                      <button type="button" disabled={busy !== null}
                        onClick={() => retirerVisuel(v.id)}
                        aria-label="Retirer ce visuel"
                        className="absolute right-1 top-1 rounded-md bg-white/90 p-1.5 text-slate-500 opacity-0 shadow-sm transition-opacity hover:text-rose-600 focus-visible:opacity-100 group-hover:opacity-100 disabled:opacity-50">
                        {busy === v.id
                          ? <Loader2 className="h-3.5 w-3.5 animate-spin" />
                          : <Trash2 className="h-3.5 w-3.5" />}
                      </button>
                    </li>
                  ))}
                </ul>
              )}

              <button type="button" disabled={busy !== null || placesGalerie <= 0}
                onClick={() => champGalerie.current?.click()}
                className="inline-flex items-center gap-2 rounded-xl border border-slate-300 px-4 py-2 text-sm font-semibold text-slate-700 transition-colors hover:bg-slate-50 disabled:opacity-50">
                {busy === 'galerie'
                  ? <Loader2 className="h-4 w-4 animate-spin" />
                  : <Upload className="h-4 w-4" />}
                {placesGalerie > 0
                  ? `Ajouter des visuels (${placesGalerie} place${placesGalerie > 1 ? 's' : ''})`
                  : 'Galerie complète'}
              </button>
              <input ref={champGalerie} type="file" hidden accept="image/*" multiple
                onChange={(e) => { ajouterGalerie(e.target.files); e.target.value = ''; }} />
            </section>

            {/* ---------------------------------------------------------- vidéo -- */}
            <section className="border-t border-slate-100 pt-7">
              <h3 className="mb-3 inline-flex items-center gap-2 text-sm font-bold text-brand-dark">
                <Video className="h-4 w-4 text-slate-400" />
                Vidéo de démonstration
              </h3>

              {media.videoUrl ? (
                <div className="rounded-xl border border-slate-200 p-4">
                  <div className="flex flex-wrap items-start justify-between gap-3">
                    <div className="min-w-0">
                      <p className="text-xs font-semibold uppercase tracking-wider text-slate-500">
                        {media.videoProvider}
                      </p>
                      <a href={media.videoUrl} target="_blank" rel="noopener noreferrer"
                        className="mt-1 block break-all text-sm text-brand underline-offset-2 hover:underline">
                        {media.videoUrl}
                      </a>
                    </div>
                    <button type="button" disabled={busy !== null}
                      onClick={() => agir('retrait', () => productMediaService.removeVideo(productId),
                        'Vidéo retirée.')}
                      className="inline-flex items-center gap-1.5 rounded-lg border border-slate-300 px-3 py-1.5 text-xs font-semibold text-slate-600 transition-colors hover:bg-slate-50 disabled:opacity-50">
                      <Trash2 className="h-3.5 w-3.5" /> Retirer
                    </button>
                  </div>

                  {/* La mise en vedette n'apparaît que lorsqu'une vidéo existe : le serveur la
                      refuserait sinon, et proposer un geste voué au refus est trompeur. */}
                  <button type="button" disabled={busy !== null}
                    onClick={() => agir('vedette',
                      () => productMediaService.setVideoPromoted(productId, !media.videoPromoted),
                      media.videoPromoted ? 'Retirée des cartes de promotion.' : 'Jouée sur les cartes de promotion.')}
                    className={`mt-4 inline-flex items-center gap-2 rounded-xl px-4 py-2 text-sm font-semibold transition-colors disabled:opacity-50 ${
                      media.videoPromoted
                        ? 'bg-brand-orange/15 text-brand-accent'
                        : 'border border-slate-300 text-slate-700 hover:bg-slate-50'
                    }`}>
                    {media.videoPromoted ? <Star className="h-4 w-4 fill-current" /> : <StarOff className="h-4 w-4" />}
                    {media.videoPromoted ? 'En vedette sur les promotions' : 'Mettre en vedette'}
                  </button>
                  {media.videoPromoted && (
                    <p className="mt-2 inline-flex items-center gap-1.5 text-xs text-slate-500">
                      <Check className="h-3.5 w-3.5 text-emerald-600" />
                      La carte de ce produit joue la vidéo au survol dans les sections Promotion.
                    </p>
                  )}
                </div>
              ) : (
                <div className="grid gap-5 sm:grid-cols-2">
                  <div className="rounded-xl border border-slate-200 p-4">
                    <p className="mb-2 text-sm font-semibold text-slate-700">Téléverser un fichier</p>
                    <button type="button" disabled={busy !== null}
                      onClick={() => champVideo.current?.click()}
                      className="inline-flex items-center gap-2 rounded-xl bg-brand px-4 py-2 text-sm font-bold text-white transition-colors hover:bg-[#0f3c35] disabled:opacity-50">
                      {busy === 'video'
                        ? <Loader2 className="h-4 w-4 animate-spin" />
                        : <Upload className="h-4 w-4" />}
                      Choisir une vidéo
                    </button>
                    <p className="mt-2 text-xs text-slate-500">
                      MP4, WebM ou MOV — {TAILLE_MAX_VIDEO_MO} Mo maximum, soit environ une minute
                      en 1080p.
                    </p>
                    <input ref={champVideo} type="file" hidden accept="video/*"
                      onChange={(e) => { deposerVideo(e.target.files?.[0]); e.target.value = ''; }} />
                  </div>

                  <div className="rounded-xl border border-slate-200 p-4">
                    <p className="mb-2 inline-flex items-center gap-1.5 text-sm font-semibold text-slate-700">
                      <Link2 className="h-4 w-4 text-slate-400" /> Ou coller un lien
                    </p>
                    <select value={hebergeur}
                      onChange={(e) => setHebergeur(e.target.value as VideoProvider)}
                      className="mb-2 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm outline-none focus:border-brand focus:ring-1 focus:ring-brand">
                      {HEBERGEURS_VIDEO.map((h) => (
                        <option key={h.value} value={h.value}>{h.label}</option>
                      ))}
                    </select>
                    <input type="url" value={lienVideo} onChange={(e) => setLienVideo(e.target.value)}
                      placeholder={exemple}
                      className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm outline-none focus:border-brand focus:ring-1 focus:ring-brand" />
                    <button type="button" disabled={busy !== null} onClick={poserLien}
                      className="mt-2 inline-flex items-center gap-2 rounded-xl border border-slate-300 px-4 py-2 text-sm font-semibold text-slate-700 transition-colors hover:bg-slate-50 disabled:opacity-50">
                      {busy === 'lien' ? <Loader2 className="h-4 w-4 animate-spin" /> : <Link2 className="h-4 w-4" />}
                      Rattacher
                    </button>
                  </div>
                </div>
              )}
            </section>
          </div>
        )}

        <div className="sticky bottom-0 flex justify-end border-t border-slate-100 bg-slate-50 px-6 py-4">
          <button type="button" onClick={onClose}
            className="rounded-xl px-5 py-2 text-sm font-semibold text-slate-600 transition-colors hover:bg-slate-100">
            Fermer
          </button>
        </div>
      </div>

      {toast && <Toast message={toast.message} type={toast.type} onClose={() => setToast(null)} />}
    </div>
  );
}
