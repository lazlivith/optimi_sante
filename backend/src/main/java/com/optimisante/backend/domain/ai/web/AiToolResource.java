package com.optimisante.backend.domain.ai.web;

import com.optimisante.backend.domain.orders.dto.OrderResponseDto;
import com.optimisante.backend.domain.orders.service.OrderService;
import com.optimisante.backend.domain.training.entity.Enrollment;
import com.optimisante.backend.domain.training.repository.EnrollmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Outils de l'assistant conversationnel, appelés <b>par le service IA Python</b> qui relaie le
 * JWT de l'utilisateur. Rien n'est spécifique à l'IA côté sécurité : ces endpoints passent par
 * la chaîne de filtres habituelle, donc le tenant, les rôles et la suppression logique
 * s'appliquent normalement. Le modèle ne peut donc voir que ce que l'utilisateur voit déjà.
 * <p>
 * Les deux endpoints publics (catalogue, formations) servent le widget de chat du site vitrine,
 * accessible sans compte.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/ai/tools")
@RequiredArgsConstructor
public class AiToolResource {

    private static final int MAX_ROWS = 12;


    /**
     * Comparaison insensible aux accents : {@code translate} remplace les caractères accentués
     * par leur équivalent non accentué des deux côtés. On évite ainsi l'extension {@code unaccent}
     * (non installée) tout en trouvant « Échographie » quand l'utilisateur tape « echographie » —
     * cas courant pour une plateforme francophone.
     */
    private static final String ACCENTS = "àáâãäçèéêëìíîïñòóôõöùúûüýÿ";
    private static final String PLAIN = "aaaaaceeeeiiiinooooouuuuyy";

    private static String unaccentSql(String expression) {
        return "TRANSLATE(LOWER(" + expression + "), '" + ACCENTS + "', '" + PLAIN + "')";
    }

    private static String unaccent(String value) {
        String lower = value.toLowerCase();
        StringBuilder out = new StringBuilder(lower.length());
        for (char c : lower.toCharArray()) {
            int idx = ACCENTS.indexOf(c);
            out.append(idx >= 0 ? PLAIN.charAt(idx) : c);
        }
        return out.toString();
    }

    /**
     * Mots de liaison retirés de la recherche. L'assistant transmet parfois la phrase entière de
     * l'utilisateur : chercher « quelles formations proposez-vous » comme une seule chaîne ne
     * remontait rien, et l'assistant annonçait un catalogue vide alors qu'il est rempli.
     */
    private static final Set<String> MOTS_VIDES = Set.of(
            "les", "des", "une", "aux", "pour", "avec", "dans", "sur", "vous", "nous", "votre",
            "vos", "mon", "mes", "que", "qui", "quoi", "quel", "quels", "quelle", "quelles",
            "est", "sont", "avez", "propose", "proposez", "proposes", "cherche", "recherche",
            "voudrais", "souhaite", "existe", "avoir", "faire", "svp", "plus", "tout", "tous",
            "toutes", "combien", "comment", "pourquoi", "lequel", "chez", "par", "pas");

    /**
     * Requêtes construites par concaténation : un bloc de texte Java ne peut pas être rouvert en
     * milieu de ligne pour y insérer une expression, il faut donc assembler les fragments. Le
     * groupe de conditions dépend du nombre de mots-clés : il est assemblé à l'exécution.
     */
    private static String productSearchSql(int tokens) {
        return "SELECT name, slug, base_price, promo_price, stock_quantity, description "
                + "FROM products "
                + "WHERE deleted_at IS NULL AND is_active = true "
                + "  AND (" + orLike(tokens, unaccentSql("name"),
                        unaccentSql("COALESCE(description, '')")) + ") "
                + "ORDER BY name LIMIT ?";
    }

    private static String trainingSearchSql(int tokens) {
        return "SELECT id, title, medical_specialty, price, duration_days, description "
                + "FROM trainings "
                + "WHERE is_published = true "
                + "  AND (" + orLike(tokens, unaccentSql("title"),
                        unaccentSql("COALESCE(description, '')"),
                        unaccentSql("COALESCE(medical_specialty, '')")) + ") "
                + "ORDER BY title LIMIT ?";
    }

    /** Un {@code LIKE ?} par colonne et par mot-clé, le tout en OR. Aucune valeur n'est concaténée. */
    private static String orLike(int tokens, String... columns) {
        StringBuilder sql = new StringBuilder();
        for (int i = 0; i < tokens; i++) {
            for (String column : columns) {
                if (sql.length() > 0) {
                    sql.append(" OR ");
                }
                sql.append(column).append(" LIKE ?");
            }
        }
        return sql.toString();
    }

    /**
     * Découpe la demande en mots-clés cherchables : accents retirés, mots de liaison et mots de
     * moins de trois lettres écartés, pluriel simple coupé pour que « formations » retrouve
     * « formation ». Une demande qui ne laisse aucun mot-clé retombe sur la chaîne entière.
     */
    private static List<String> motsCles(String query) {
        List<String> mots = new ArrayList<>();
        for (String brut : unaccent(query).split("[^a-z0-9]+")) {
            if (brut.length() < 3 || MOTS_VIDES.contains(brut)) {
                continue;
            }
            String mot = brut.length() > 4 && brut.endsWith("s") ? brut.substring(0, brut.length() - 1) : brut;
            if (!mots.contains(mot)) {
                mots.add(mot);
            }
            if (mots.size() == 5) {
                break;
            }
        }
        if (mots.isEmpty()) {
            mots.add(unaccent(query).trim());
        }
        return mots;
    }

    /** Les paramètres de la requête : chaque mot-clé une fois par colonne, puis la limite. */
    private static Object[] parametres(List<String> mots, int columns) {
        List<Object> args = new ArrayList<>();
        for (String mot : mots) {
            for (int c = 0; c < columns; c++) {
                args.add("%" + mot + "%");
            }
        }
        args.add(MAX_ROWS);
        return args.toArray();
    }

    private final JdbcTemplate jdbc;
    private final OrderService orderService;
    private final EnrollmentRepository enrollmentRepository;

    // ------------------------------------------------------------------ public

    @GetMapping("/catalog/search")
    @Transactional(readOnly = true)
    public List<Map<String, Object>> searchCatalog(@RequestParam(name = "q", defaultValue = "") String query) {
        List<String> mots = motsCles(query);
        List<Map<String, Object>> results = new ArrayList<>();

        try {
            results.addAll(jdbc.query(
                    productSearchSql(mots.size()),
                    (rs, i) -> {
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("kind", "produit");
                        row.put("name", rs.getString("name"));
                        row.put("url", "/product/" + rs.getString("slug"));
                        row.put("priceEur", rs.getBigDecimal("base_price"));
                        row.put("promoPriceEur", rs.getBigDecimal("promo_price"));
                        row.put("inStock", rs.getInt("stock_quantity") > 0);
                        row.put("description", truncate(rs.getString("description")));
                        return row;
                    },
                    parametres(mots, 2)));
        } catch (Exception e) {
            log.warn("AI tool catalog search (products) failed: {}", e.getMessage());
        }

        try {
            results.addAll(jdbc.query(
                    trainingSearchSql(mots.size()),
                    (rs, i) -> {
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("kind", "formation");
                        row.put("name", rs.getString("title"));
                        row.put("url", "/formations/" + rs.getString("id"));
                        row.put("specialty", rs.getString("medical_specialty"));
                        row.put("priceEur", rs.getBigDecimal("price"));
                        row.put("durationDays", rs.getObject("duration_days"));
                        row.put("description", truncate(rs.getString("description")));
                        return row;
                    },
                    parametres(mots, 3)));
        } catch (Exception e) {
            log.warn("AI tool catalog search (trainings) failed: {}", e.getMessage());
        }

        return results;
    }

    @GetMapping("/trainings")
    @Transactional(readOnly = true)
    public List<Map<String, Object>> trainings() {
        try {
            return jdbc.query(
                    """
                    SELECT t.id, t.title, t.medical_specialty, t.price, t.duration_days, t.description,
                           s.start_date, s.end_date, s.status, s.available_seats, s.location
                    FROM trainings t
                    LEFT JOIN training_sessions s ON s.training_id = t.id
                    WHERE t.is_published = true
                    ORDER BY t.title, s.start_date
                    LIMIT 40
                    """,
                    (rs, i) -> {
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("training", rs.getString("title"));
                        row.put("url", "/formations/" + rs.getString("id"));
                        row.put("specialty", rs.getString("medical_specialty"));
                        row.put("priceEur", rs.getBigDecimal("price"));
                        row.put("durationDays", rs.getObject("duration_days"));
                        row.put("description", truncate(rs.getString("description")));
                        row.put("sessionStart", rs.getObject("start_date"));
                        row.put("sessionEnd", rs.getObject("end_date"));
                        row.put("sessionStatus", rs.getString("status"));
                        row.put("sessionLocation", rs.getString("location"));
                        row.put("availableSeats", rs.getObject("available_seats"));
                        return row;
                    });
        } catch (Exception e) {
            log.warn("AI tool trainings failed: {}", e.getMessage());
            return List.of();
        }
    }

    // ------------------------------------------------------------------ authentifié

    @GetMapping("/my-orders")
    @Transactional(readOnly = true)
    public List<Map<String, Object>> myOrders() {
        return orderService.getMyOrders(PageRequest.of(0, 10)).getContent().stream()
                .map(AiToolResource::orderRow)
                .toList();
    }

    @GetMapping("/my-enrollments")
    @Transactional(readOnly = true)
    public List<Map<String, Object>> myEnrollments() {
        UUID userId = currentUserId();
        return enrollmentRepository.findByDoctorId(userId).stream()
                .map(AiToolResource::enrollmentRow)
                .toList();
    }

    // ------------------------------------------------------------------ mapping

    private static Map<String, Object> orderRow(OrderResponseDto o) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("orderNumber", o.orderNumber());
        row.put("status", o.status());
        row.put("paymentStatus", o.paymentStatus());
        row.put("isQuote", o.isQuote());
        row.put("totalEur", o.totalAmount());
        row.put("createdAt", o.createdAt());
        row.put("url", "/my-orders");
        return row;
    }

    private static Map<String, Object> enrollmentRow(Enrollment e) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("enrollmentId", e.getId());
        row.put("status", e.getStatus() == null ? null : e.getStatus().name());
        row.put("submittedAt", e.getSubmittedAt());
        row.put("hasDiploma", e.getDiplomaUrl() != null);
        row.put("hasPassport", e.getPassportUrl() != null);
        row.put("hasMedicalBoardRegistration", e.getMedicalBoardRegistrationUrl() != null);
        row.put("conventionIssued", e.getConventionS3Key() != null);
        row.put("url", "/doctor/enrollments/" + e.getId());
        try {
            row.put("training", e.getSession().getTraining().getTitle());
            row.put("sessionStart", e.getSession().getStartDate());
        } catch (Exception ignored) {
            // Association absente ou non chargée : l'assistant s'en passe.
        }
        return row;
    }

    private static String truncate(String text) {
        if (text == null) {
            return null;
        }
        String flat = text.replaceAll("\\s+", " ").trim();
        return flat.length() <= 300 ? flat : flat.substring(0, 300) + "…";
    }

    private static UUID currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            throw new IllegalStateException("Utilisateur non authentifié");
        }
        return UUID.fromString(auth.getPrincipal().toString());
    }
}
