/** Página provisória para telas do menu que ainda serão implementadas. */
import { Card, TituloPagina, Trilha } from "../components/ui";

export default function EmConstrucao({ titulo }: { titulo: string }) {
  return (
    <>
      <Trilha itens={[{ rotulo: titulo }]} />
      <TituloPagina
        titulo={titulo}
        subtitulo="Esta tela será implementada nas próximas etapas."
      />
      <Card className="p-10 text-center text-[14px] text-[#66707d]">
        🚧 Em construção
      </Card>
    </>
  );
}
