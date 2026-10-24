package dev.monkeypatch.rctiming.domain.boardview;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.monkeypatch.rctiming.domain.EntityNotFoundException;
import dev.monkeypatch.rctiming.domain.StateConflictException;
import dev.monkeypatch.rctiming.domain.audit.Actor;
import dev.monkeypatch.rctiming.domain.audit.AuditService;
import dev.monkeypatch.rctiming.persistence.ReadTransaction;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Saved board views (#481): the screens an official builds from panels and opens at {@code /boards/view/{slug}}.
 * Called by {@code AdminBoardViewController} (changes, in one write transaction each with an audit row) and by {@code
 * PublicBoardViewController} (reads). A view holds only layout, so nothing here touches timing.
 */
@Service
@Transactional
public class BoardViewService {

    private static final TypeReference<List<BoardPanel>> PANELS = new TypeReference<>() { };

    private final BoardViewRepository repository;
    private final AuditService audit;
    private final ObjectMapper json;

    public BoardViewService(BoardViewRepository repository, AuditService audit, ObjectMapper json) {
        this.repository = repository;
        this.audit = audit;
        this.json = json;
    }

    /** Every view, by name. */
    @ReadTransaction
    public List<BoardView> list() {
        return repository.findAllByName();
    }

    /** One view by its address name. @throws EntityNotFoundException (404) if there is none */
    @ReadTransaction
    public BoardView getBySlug(String slug) {
        return repository.findBySlug(slug)
                .orElseThrow(() -> new EntityNotFoundException("There is no board view called " + slug));
    }

    /** One view by id. @throws EntityNotFoundException (404) if there is none */
    @ReadTransaction
    public BoardView get(Long id) {
        return repository.getOrThrow(id);
    }

    /** A view's panels, read from its stored JSON; touches no table, so it opens no transaction. */
    @Transactional(propagation = Propagation.SUPPORTS)
    public List<BoardPanel> panelsOf(BoardView view) {
        try {
            return json.readValue(view.getPanels(), PANELS);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("The panels of board view " + view.getSlug() + " cannot be read", e);
        }
    }

    /**
     * Adds a view and records {@code BOARD_VIEW_CREATED}.
     *
     * @throws IllegalArgumentException (400) for a bad address name, name or layout
     * @throws StateConflictException (409) if the address name is taken
     */
    public BoardView create(Actor actor, String slug, String name, int columns, List<BoardPanel> panels) {
        BoardView view = new BoardView();
        fill(view, slug, name, columns, panels);
        BoardView saved = repository.save(view);
        audit.entry(actor, "BOARD_VIEW_CREATED").entity("board_view", saved.getId())
                .summary("Added the board view " + saved.getName()).after(values(saved)).record();
        return saved;
    }

    /** Changes a view and records {@code BOARD_VIEW_UPDATED}; throws as {@link #create} does, and 404 if it is missing. */
    public BoardView update(Actor actor, Long id, String slug, String name, int columns, List<BoardPanel> panels) {
        BoardView view = get(id);
        Map<String, Object> before = values(view);
        fill(view, slug, name, columns, panels);
        BoardView saved = repository.save(view);
        audit.entry(actor, "BOARD_VIEW_UPDATED").entity("board_view", id)
                .summary("Changed the board view " + saved.getName()).before(before).after(values(saved)).record();
        return saved;
    }

    /** Removes a view and records {@code BOARD_VIEW_DELETED}. @throws EntityNotFoundException (404) if it is missing */
    public void delete(Actor actor, Long id) {
        BoardView view = get(id);
        Map<String, Object> before = values(view);
        repository.deleteById(id);
        audit.entry(actor, "BOARD_VIEW_DELETED").entity("board_view", id)
                .summary("Removed the board view " + view.getName()).before(before).record();
    }

    private void fill(BoardView view, String slug, String name, int columns, List<BoardPanel> panels) {
        String cleanSlug = BoardViewRules.cleanSlug(slug);
        repository.findBySlug(cleanSlug).filter(other -> !other.getId().equals(view.getId())).ifPresent(other -> {
            throw new StateConflictException("There is already a board view at /boards/view/" + cleanSlug);
        });
        List<BoardPanel> checked = BoardViewRules.validate(columns, panels);
        view.setSlug(cleanSlug);
        view.setName(BoardViewRules.cleanName(name));
        view.setColumns(columns);
        try {
            view.setPanels(json.writeValueAsString(checked));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("The panels cannot be written", e);
        }
    }

    private static Map<String, Object> values(BoardView v) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("slug", v.getSlug());
        m.put("name", v.getName());
        m.put("columns", v.getColumns());
        m.put("panels", v.getPanels());
        return m;
    }
}
