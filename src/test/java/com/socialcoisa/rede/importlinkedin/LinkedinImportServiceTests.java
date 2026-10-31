package com.socialcoisa.rede.importlinkedin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import com.socialcoisa.rede.shared.web.ApiException;

/**
 * O parser do ARQUIVO DE DADOS do LinkedIn: CSVs com BOM, campos com aspas/vírgula,
 * datas "YYYY-MM" e "MMM YYYY" (pt/en) e a recusa do que não é exportação do LinkedIn.
 *
 * Nota de Java: as linhas com aspas ESCAPADAS de CSV ("") são construídas por
 * concatenação de strings normais — dentro de text block, qualquer """ encerra o
 * bloco (e \u0022 não salva: escapes unicode viram aspa ANTES do lexer).
 */
class LinkedinImportServiceTests {

	private final LinkedinImportService service = new LinkedinImportService();

	private MockMultipartFile zip(String... csvs) {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
			for (int i = 0; i < csvs.length; i += 2) {
				zip.putNextEntry(new ZipEntry(csvs[i]));
				zip.write(csvs[i + 1].getBytes(StandardCharsets.UTF_8));
				zip.closeEntry();
			}
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		return new MockMultipartFile("file", "linkedin.zip", "application/zip", bytes.toByteArray());
	}

	private static final String POSITIONS_HEADER = "\"Position Title\",\"Company Name\",\"Location\",\"Description\",\"Started On\",\"Finished On\"";

	@Test
	void parsesPositionsFullRoundTrip() {
		// A linha com aspas ESCAPADAS de CSV ("") vem por concatenação (ver nota da classe).
		String linhaComAspas = "\"Engenheira de Software\",\"Énóis, LTDA\",\"Remoto\","
			+ "\"Construí coisas, com vírgulas, e \"\"aspas\"\" dentro.\""
			+ ",\"2020-01\",\"2022-06\"";
		var preview = service.parse(zip(
			"Positions.csv", POSITIONS_HEADER + "\n" + linhaComAspas + "\n"
				+ "\"Tech Lead\",\"Nubank\",\"São Paulo\",\"Liderança de time\",\"\",\"\"",
			"Education.csv", "",
			"Certifications.csv", "",
			"Projects.csv", ""));

		assertThat(preview.experiences()).hasSize(2);
		var first = preview.experiences().get(0);
		assertThat(first.title()).isEqualTo("Engenheira de Software");
		assertThat(first.company()).isEqualTo("Énóis, LTDA");
		assertThat(first.description()).isEqualTo("Construí coisas, com vírgulas, e \"aspas\" dentro.");
		assertThat(first.startDate()).isEqualTo("2020-01-01");
		assertThat(first.endDate()).isEqualTo("2022-06-01");
		assertThat(first.currentlyHere()).isFalse();

		var second = preview.experiences().get(1);
		assertThat(second.company()).isEqualTo("Nubank");
		assertThat(second.startDate()).isNull();
		assertThat(second.currentlyHere()).isTrue();
	}

	/**
	 * Cabeçalhos do export ATUAL (o que a pessoa baixa hoje): `Title` (era `Position Title`) e
	 * `Degree Name` (era `Degree`). Sem os fallbacks, toda experiência vinha SEM cargo — e o
	 * import parecia ter "reconhecido" só as empresas.
	 *
	 * Datas saem em ISO completo: o perfil recebe `LocalDate`, e "2026-10" fazia o save falhar com
	 * "requisição inválida".
	 */
	@Test
	void readsTheCurrentExportHeadersAndProducesIsoDates() {
		var preview = service.parse(zip(
			"Positions.csv", "Company Name,Title,Description,Location,Started On,Finished On\n"
				+ "\"É Nóis\",\"Desenvolvedora\",\"\",\"remoto\",\"Oct 2026\",\"\"",
			"Education.csv", "School Name,Start Date,End Date,Notes,Degree Name,Activities\n"
				+ "\"UFPR\",\"2012-03\",\"2015-12\",\"\",\"Bacharelado\",\"\"",
			"Profile.csv", "First Name,Last Name,Headline,Summary,Industry,Geo Location\n"
				+ "\"Mel\",\"Santos\",\"Dev\",\"resumo\",\"Tech\",\"Arapongas, PR\""));

		var experience = preview.experiences().get(0);
		assertThat(experience.title()).isEqualTo("Desenvolvedora");
		assertThat(experience.startDate()).isEqualTo("2026-10-01");
		assertThat(experience.currentlyHere()).isTrue();

		var education = preview.education().get(0);
		assertThat(education.degree()).isEqualTo("Bacharelado");
		assertThat(education.startDate()).isEqualTo("2012-03-01");
	}

	@Test
	void parsesEducationCertificationsAndProjects() {
		var preview = service.parse(zip(
			"Positions.csv", POSITIONS_HEADER + "\n\"Dev\",\"Acme\",\"Remoto\",\"\",\"2020-01\",\"\"",
			"Education.csv", "\"School Name\",\"Degree\",\"Field of Study\",\"Notes\",\"Start Date\",\"End Date\"\n"
				+ "\"UFPE\",\"Bacharelado\",\"Ciência da Computação\",\"\",\"2012-03\",\"2017-12\"",
			"Certifications.csv", "\"Name\",\"Authority\",\"URL\",\"Time Period\"\n"
				+ "\"AWS Solutions Architect\",\"Amazon Web Services\",\"https://credly.com/x\",\"2021-05\"",
			"Projects.csv", "\"Title\",\"Description\",\"URL\",\"Started On\",\"Finished On\"\n"
				+ "\"compilador\",\"um compilador de verdade\",\"https://github.com/eu/comp\",\"2023-02\",\"2024-01\""));

		assertThat(preview.education()).hasSize(1);
		assertThat(preview.education().get(0).school()).isEqualTo("UFPE");
		assertThat(preview.education().get(0).startDate()).isEqualTo("2012-03-01");
		assertThat(preview.certifications()).hasSize(1);
		assertThat(preview.certifications().get(0).name()).isEqualTo("AWS Solutions Architect");
		assertThat(preview.certifications().get(0).issueDate()).isEqualTo("2021-05-01");
		assertThat(preview.projects()).hasSize(1);
		assertThat(preview.projects().get(0).url()).isEqualTo("https://github.com/eu/comp");
		assertThat(preview.found()).isEqualTo(4);
	}

	@Test
	void parsesPortugueseMonthNames() {
		var preview = service.parse(zip(
			"Positions.csv", "\"Position Title\",\"Company Name\",\"Started On\"\n\"Dev\",\"Acme\",\"mar de 2021\"",
			"Education.csv", "", "Certifications.csv", "", "Projects.csv", ""));

		assertThat(preview.experiences().get(0).startDate()).isEqualTo("2021-03-01");
	}

	@Test
	void refusesNonLinkedinFile() {
		assertThatThrownBy(() -> service.parse(zip(
			"outra-coisa.csv", "nada aqui")))
			.isInstanceOf(ApiException.class)
			.hasMessageContaining("Não encontrei os CSVs");
	}

	@Test
	void parsesTheFastExportWithProfileOnly() {
		// O export RÁPIDO ("fast file") não tem Positions.csv: aceitamos com o que tem (perfil),
		// em vez de beco sem saída — relato de quem baixou Basic_LinkedInDataExport.
		var preview = service.parse(zip(
			"Registration.csv", "foo,bar\n1,2",
			"Profile.csv", "\"First Name\",\"Last Name\",\"Headline\",\"Summary\",\"Industry\",\"Geo Location\"\n"
				+ "\"Mel\",\"Santos\",\"Criadora da linguagem Kof\",\" Sou engenheira de software\",\"Technology\",\"Arapongas, Paraná, Brasil\""));

		assertThat(preview.experiences()).isEmpty();
		assertThat(preview.education()).isEmpty();
		assertThat(preview.profile()).isNotNull();
		assertThat(preview.profile().name()).isEqualTo("Mel Santos");
		assertThat(preview.profile().headline()).isEqualTo("Criadora da linguagem Kof");
		assertThat(preview.profile().location()).isEqualTo("Arapongas, Paraná, Brasil");
		assertThat(preview.found()).isEqualTo(1);
	}

	@Test
	void refusesEmptyAndHuge() {
		assertThatThrownBy(() -> service.parse(null)).isInstanceOf(ApiException.class);
		assertThatThrownBy(() -> service.parse(new MockMultipartFile("file", "x.zip", "application/zip",
			new byte[5 * 1024 * 1024 + 1]))).isInstanceOf(ApiException.class);
	}

	@Test
	void parsesProfileSkillsPublicationsAndCountsTheRest() {
		var preview = service.parse(zip(
			"Profile.csv",
			"\"First Name\",\"Last Name\",\"Headline\",\"Summary\",\"Geo Location\",\"Industry\"\n"
				+ "\"Ada\",\"Silva\",\"Dev backend\",\"Gosto de café e código.\",\"São Paulo, Brasil\",\"Software\"",
			"Skills.csv",
			"\"Name\"\n\"Java\"\n\"Spring\"\n\"Kof\"",
			"Positions.csv",
			POSITIONS_HEADER + "\n\"Dev\",\"Acme\",\"SP\",\"\",\"2020-01\",\"\"",
			"Publications.csv",
			"\"Title\",\"Description\",\"Url\",\"Published On\"\n\"Artigo de IA\",\"Sobre LLMs\",\"https://ex.com/ia\",\"2023-05\"",
			"Languages.csv",
			"\"Name\",\"Proficiency\"\n\"Português\",\"Nativo\"\n\"Inglês\",\"Fluente\"",
			"Honors & Awards.csv",
			"\"Title\",\"Issuer\"\n\"Hackathon\",\"Acme\"",
			"Courses.csv",
			"\"Name\",\"Number\"\n\"Spring Boot\",\"1\"",
			"Volunteering.csv",
			"\"Company Name\",\"Role\"\n\"ONG\",\"Mentora\""));

		// Perfil: só os campos com casa no Énóis.
		assertThat(preview.profile()).isNotNull();
		assertThat(preview.profile().name()).isEqualTo("Ada Silva");
		assertThat(preview.profile().headline()).isEqualTo("Dev backend");
		assertThat(preview.profile().location()).isEqualTo("São Paulo, Brasil");
		assertThat(preview.profile().summary()).isEqualTo("Gosto de café e código.");

		// Skills: lista do período, na ordem.
		assertThat(preview.skills()).containsExactly("Java", "Spring", "Kof");

		// Publicação vira projeto (título/descrição/link/data).
		assertThat(preview.projects()).hasSize(1);
		assertThat(preview.projects().get(0).title()).isEqualTo("Artigo de IA");
		assertThat(preview.projects().get(0).url()).isEqualTo("https://ex.com/ia");

		// O que não tem estrutura é CONTADO (nada some em silêncio).
		assertThat(preview.semEstrutura()).containsEntry("Idiomas", 2).containsEntry("Prêmios", 1)
			.containsEntry("Cursos", 1).containsEntry("Voluntariado", 1);

		// "found" conta o que dá para importar (posição + skill×3 + publicação + perfil).
		assertThat(preview.found()).isGreaterThanOrEqualTo(6);
	}

	@Test
	void parsesPastedProfileText() {
		String colado = """
			Mel Santos
			Desenvolvedora Java · Énóis

			Sobre
			Construo coisas na internet. Gosto de café e código.

			Experiência
			Desenvolvedora Backend
			Énóis
			mar de 2021 - o momento · 4 anos 2 meses
			São Paulo, Brasil
			Construí o feed, a carteira e a lojinha.
			Ver mais

			Desenvolvedora
			Acme Ltda
			jan de 2019 - fev de 2021 · 2 anos 2 meses
			Remoto
			APIs em Java.

			Formação acadêmica
			Universidade de São Paulo
			Bacharelado, Ciência da Computação
			2015 - 2019

			Licenças e certificações
			Oracle Certified Professional: Java SE
			Oracle
			Emitido em mar de 2022 · Sem validade
			Credencial ID 123 | https://certs.example.com/ocp

			Projetos
			KofBot
			jan de 2020 - dez de 2020
			Um bot de doações.

			Habilidades
			Java, Spring Boot
			PostgreSQL
			""";

		var preview = service.parseTexto(colado);

		assertThat(preview.experiences()).hasSize(2);
		var primeira = preview.experiences().get(0);
		assertThat(primeira.title()).isEqualTo("Desenvolvedora Backend");
		assertThat(primeira.company()).isEqualTo("Énóis");
		assertThat(primeira.location()).isEqualTo("São Paulo, Brasil");
		assertThat(primeira.startDate()).isEqualTo("2021-03-01");
		assertThat(primeira.endDate()).isNull();
		assertThat(primeira.currentlyHere()).isTrue();
		assertThat(primeira.description()).isEqualTo("Construí o feed, a carteira e a lojinha.");

		var segunda = preview.experiences().get(1);
		assertThat(segunda.title()).isEqualTo("Desenvolvedora");
		assertThat(segunda.company()).isEqualTo("Acme Ltda");
		assertThat(segunda.startDate()).isEqualTo("2019-01-01");
		assertThat(segunda.endDate()).isEqualTo("2021-02-01");
		assertThat(segunda.currentlyHere()).isFalse();
		// O "Ver mais" do LinkedIn não vira descrição e o título do próximo item não invade.
		assertThat(segunda.description()).doesNotContain("Desenvolvedora").doesNotContain("Ver mais");

		assertThat(preview.education()).hasSize(1);
		assertThat(preview.education().get(0).school()).isEqualTo("Universidade de São Paulo");
		assertThat(preview.education().get(0).degree()).isEqualTo("Bacharelado, Ciência da Computação");
		assertThat(preview.education().get(0).startDate()).isEqualTo("2015-01-01");
		assertThat(preview.education().get(0).endDate()).isEqualTo("2019-12-01");

		assertThat(preview.certifications()).hasSize(1);
		var cert = preview.certifications().get(0);
		assertThat(cert.name()).isEqualTo("Oracle Certified Professional: Java SE");
		assertThat(cert.issuer()).isEqualTo("Oracle");
		assertThat(cert.issueDate()).isEqualTo("2022-03-01");
		assertThat(cert.credentialUrl()).isEqualTo("https://certs.example.com/ocp");

		assertThat(preview.projects()).hasSize(1);
		assertThat(preview.projects().get(0).title()).isEqualTo("KofBot");
		assertThat(preview.projects().get(0).startDate()).isEqualTo("2020-01-01");
		assertThat(preview.projects().get(0).endDate()).isEqualTo("2020-12-01");

		assertThat(preview.skills()).containsExactly("Java", "Spring Boot", "PostgreSQL");

		assertThat(preview.profile()).isNotNull();
		assertThat(preview.profile().name()).isEqualTo("Mel Santos");
		assertThat(preview.profile().headline()).isEqualTo("Desenvolvedora Java · Énóis");
		assertThat(preview.profile().summary()).contains("Gosto de café");
	}

	static MockMultipartFile highRatioZip(String prefix, String suffix, int count, int size) {
		return zipOf(prefix, suffix, count, new byte[size]);
	}

	/** ZIP with the given entries, in order. */
	static MockMultipartFile namedZip(Map<String, byte[]> entries) {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
			for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
				zip.putNextEntry(new ZipEntry(entry.getKey()));
				zip.write(entry.getValue());
				zip.closeEntry();
			}
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		return new MockMultipartFile("file", "linkedin.zip", "application/zip", bytes.toByteArray());
	}

	/** A Positions.csv that inflates to {@code size} bytes of zeros: a bomb in an entry that is read. */
	static MockMultipartFile positionsBomb(int size) {
		return namedZip(Map.of("Basic_LinkedInDataExport/Positions.csv", new byte[size]));
	}

	/** ZIP with {@code count} entries named {@code prefix + i + suffix}, each holding {@code content}. */
	static MockMultipartFile zipOf(String prefix, String suffix, int count, byte[] zeros) {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
			for (int i = 0; i < count; i++) {
				zip.putNextEntry(new ZipEntry(prefix + i + suffix));
				zip.write(zeros);
				zip.closeEntry();
			}
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		return new MockMultipartFile("file", "linkedin.zip", "application/zip", bytes.toByteArray());
	}

	private void assertTooLarge(MockMultipartFile file) {
		assertThatThrownBy(() -> service.parse(file))
			.isInstanceOfSatisfying(ApiException.class, error -> {
				assertThat(error.status()).isEqualTo(413);
				assertThat(error.code()).isEqualTo("IMPORT_TOO_LARGE");
			});
	}

	@Test
	void rejectsHighRatioCsvEntry() {
		MockMultipartFile bomb = positionsBomb(60 * 1024 * 1024);
		assertThat(bomb.getSize()).isLessThan(200 * 1024);
		assertTooLarge(bomb);
	}

	@Test
	void entriesTheImporterDoesNotUseAreNeverInflated() {
		// A large photo or message dump in a full export must not cost the import: it is skipped
		// from the central directory, so even a 60 MB bomb there is harmless.
		var entries = new java.util.LinkedHashMap<String, byte[]>();
		entries.put("Basic_LinkedInDataExport/Rich_Media/huge.bin", new byte[60 * 1024 * 1024]);
		entries.put("Basic_LinkedInDataExport/messages.csv", new byte[60 * 1024 * 1024]);
		entries.put("Basic_LinkedInDataExport/Positions.csv",
			"Company Name,Title,Started On\nAcme,Engenheira,Jan 2020\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));

		var preview = service.parse(namedZip(entries));

		assertThat(preview.experiences()).hasSize(1);
		assertThat(preview.experiences().get(0).company()).isEqualTo("Acme");
	}

	@Test
	void rejectsWhenTheTotalCrossesTheBudget() {
		var small = new LinkedinImportService(1024 * 1024);
		// Incompressible content: only the total budget can stop it, not the ratio cap.
		byte[] noise = new byte[512 * 1024];
		new java.util.Random(7).nextBytes(noise);
		assertThatThrownBy(() -> small.parse(namedZip(Map.of("Positions.csv", noise, "Education.csv", noise,
				"Skills.csv", noise))))
			.isInstanceOfSatisfying(ApiException.class, error -> assertThat(error.code()).isEqualTo("IMPORT_TOO_LARGE"));
	}

	@Test
	void rejectsTooManyEntries() {
		assertTooLarge(highRatioZip("Entry", ".csv", 51, 1));
	}

	@Test
	void rejectsEmptyPastedText() {
		assertThatThrownBy(() -> service.parseTexto("   ")).isInstanceOf(ApiException.class);
	}
}