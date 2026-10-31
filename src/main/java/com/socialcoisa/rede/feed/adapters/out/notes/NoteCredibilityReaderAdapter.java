package com.socialcoisa.rede.feed.adapters.out.notes;

import org.springframework.stereotype.Component;

import com.socialcoisa.rede.feed.application.port.out.PostNoteRepository;
import com.socialcoisa.rede.feed.domain.PostNoteStatus;
import com.socialcoisa.rede.profile.application.port.out.NoteCredibilityReader;

@Component
class NoteCredibilityReaderAdapter implements NoteCredibilityReader {

	private final PostNoteRepository notes;

	NoteCredibilityReaderAdapter(PostNoteRepository notes) {
		this.notes = notes;
	}

	@Override
	public int countDeniedByUser(String userId) {
		return notes.countByAuthorAndStatus(userId, PostNoteStatus.DENIED);
	}

	@Override
	public int countApprovedByUser(String userId) {
		return notes.countByAuthorAndStatus(userId, PostNoteStatus.APPROVED);
	}
}