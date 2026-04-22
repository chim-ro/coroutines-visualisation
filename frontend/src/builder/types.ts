import { BuilderType, JobType, FailureConfig } from '../types';

export interface BuilderNodeConfig {
  id: string;
  displayName: string;
  builder: BuilderType;
  jobType: JobType;
  children: BuilderNodeConfig[];
  failure?: FailureConfig;
}

export interface CustomScenario {
  id: string;
  name: string;
  tree: BuilderNodeConfig;
  createdAt: number;
}
