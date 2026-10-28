package com.socialcoisa.rede.mentoring.application;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.socialcoisa.rede.mentoring.application.port.out.MentorRepository;
import com.socialcoisa.rede.mentoring.domain.Mentor;
import com.socialcoisa.rede.mentoring.domain.MentorSession;
import com.socialcoisa.rede.mentoring.domain.MentorSlot;
import com.socialcoisa.rede.shared.web.ApiException;

@Service
public class MentorService {

	private final MentorRepository repository;
	private final Clock clock;

	public MentorService(MentorRepository repository, Clock clock) {
		this.repository = repository;
		this.clock = clock;
	}

	@Transactional
	public Mentor create(String userId, List<String> expertise, String price) {
		return create(userId, expertise, price, null);
	}

	@Transactional
	public Mentor create(String userId, List<String> expertise, String price, String banner) {
		if (repository.findByUserId(userId).isPresent()) {
			throw new ApiException(409, "ALREADY_MENTOR", "Voce ja esta cadastrado como mentor.");
		}
		String id = "mentor_" + UUID.randomUUID().toString().replace("-", "");
		Mentor mentor = Mentor.create(id, userId, expertise, price, banner, clock.instant());
		repository.save(mentor);
		return mentor;
	}

	@Transactional
	public Mentor update(String mentorId, List<String> expertise, String price, String userId) {
		return update(mentorId, expertise, price, null, userId);
	}

	@Transactional
	public Mentor update(String mentorId, List<String> expertise, String price, String banner, String userId) {
		Mentor mentor = repository.findById(mentorId)
			.orElseThrow(() -> new ApiException(404, "MENTOR_NOT_FOUND", "Mentor nao encontrado."));
		if (!mentor.userId().equals(userId)) {
			throw new ApiException(403, "FORBIDDEN", "Voce nao e o dono deste perfil de mentor.");
		}
		Mentor updated = mentor.update(expertise, price, banner, clock.instant());
		repository.save(updated);
		return updated;
	}

	@Transactional
	public MentorSlot createSlot(String mentorId, String userId, java.time.Instant startsAt, java.time.Instant endsAt) {
		Mentor mentor = repository.findById(mentorId)
			.orElseThrow(() -> new ApiException(404, "MENTOR_NOT_FOUND", "Mentor nao encontrado."));
		if (!mentor.userId().equals(userId)) {
			throw new ApiException(403, "FORBIDDEN", "Voce nao e o dono deste perfil de mentor.");
		}
		String id = "slot_" + UUID.randomUUID().toString().replace("-", "");
		MentorSlot slot = MentorSlot.create(id, mentorId, startsAt, endsAt);
		repository.saveSlot(slot);
		return slot;
	}

	/** Remove um horario publicado; so o dono. Horario ja reservado nao pode sumir. */
	@Transactional
	public void deleteSlot(String mentorId, String slotId, String userId) {
		Mentor mentor = repository.findById(mentorId)
			.orElseThrow(() -> new ApiException(404, "MENTOR_NOT_FOUND", "Mentor nao encontrado."));
		if (!mentor.userId().equals(userId)) {
			throw new ApiException(403, "FORBIDDEN", "Voce nao e o dono deste perfil de mentor.");
		}
		MentorSlot slot = repository.findSlotById(slotId)
			.orElseThrow(() -> new ApiException(404, "SLOT_NOT_FOUND", "Horario nao encontrado."));
		// O slot tem que pertencer ao mentor da URL; sem isso, o dono de um mentor apagava o
		// horario de outro passando o próprio mentorId (EN-03).
		if (!slot.mentorId().equals(mentor.id())) {
			throw new ApiException(404, "SLOT_NOT_FOUND", "Horario nao encontrado.");
		}
		if (!slot.available()) {
			throw new ApiException(409, "SLOT_BOOKED", "Este horario ja foi reservado.");
		}
		repository.deleteSlot(slotId);
	}

	@Transactional
	public MentorSession scheduleSession(String mentorId, String menteeId, String slotId, String message) {
		Mentor mentor = repository.findById(mentorId)
			.orElseThrow(() -> new ApiException(404, "MENTOR_NOT_FOUND", "Mentor nao encontrado."));
		MentorSlot slot = repository.findSlotById(slotId)
			.orElseThrow(() -> new ApiException(404, "SLOT_NOT_FOUND", "Horario nao encontrado."));
		if (!slot.mentorId().equals(mentor.id())) {
			throw new ApiException(404, "SLOT_NOT_FOUND", "Horario nao encontrado.");
		}
		if (!slot.available()) {
			throw new ApiException(409, "SLOT_UNAVAILABLE", "Horario nao esta mais disponivel.");
		}
		repository.saveSlot(slot.book());
		String id = "mentor_session_" + UUID.randomUUID().toString().replace("-", "");
		MentorSession session = MentorSession.schedule(id, mentorId, menteeId, slotId, message, clock.instant());
		repository.saveSession(session);
		return session;
	}

	@Transactional
	public void delete(String mentorId, String userId) {
		Mentor mentor = repository.findById(mentorId)
			.orElseThrow(() -> new ApiException(404, "MENTOR_NOT_FOUND", "Mentor nao encontrado."));
		if (!mentor.userId().equals(userId)) {
			throw new ApiException(403, "FORBIDDEN", "Voce nao e o dono deste perfil de mentor.");
		}
		repository.delete(mentorId);
	}

	public List<Mentor> list(String expertise, String query, java.time.Instant before, int limit) {
		return repository.findAll(expertise, query, before, limit);
	}

	public Mentor getById(String id) {
		return repository.findById(id)
			.orElseThrow(() -> new ApiException(404, "MENTOR_NOT_FOUND", "Mentor nao encontrado."));
	}

	public List<MentorSlot> getSlots(String mentorId) {
		return repository.findSlotsByMentor(mentorId);
	}

	/**
	 * Proximo horario livre do mentor (ISO-8601) ou null quando nao ha agenda publicada.
	 *
	 * Antes o campo vinha sempre nulo na resposta, entao a tela nao tinha como dizer quando a pessoa
	 * atende nem por onde comecar o agendamento.
	 */
	public String nextAvailableSlot(String mentorId) {
		java.time.Instant now = clock.instant();
		return repository.findSlotsByMentor(mentorId).stream()
			.filter(MentorSlot::available)
			.filter(slot -> slot.startsAt() != null && slot.startsAt().isAfter(now))
			.sorted(java.util.Comparator.comparing(MentorSlot::startsAt))
			.map(slot -> slot.startsAt().toString())
			.findFirst()
			.orElse(null);
	}

}
